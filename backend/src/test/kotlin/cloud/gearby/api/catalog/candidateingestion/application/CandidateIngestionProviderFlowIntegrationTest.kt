package cloud.gearby.api.catalog.candidateingestion.application

import cloud.gearby.api.catalog.candidateingestion.application.command.ProviderIngestionCommand
import cloud.gearby.api.catalog.candidateingestion.application.port.ProviderCandidatePage
import cloud.gearby.api.catalog.candidateingestion.application.port.ProviderFailure
import cloud.gearby.api.catalog.candidateingestion.application.port.ProviderFailureCategory
import cloud.gearby.api.catalog.candidateingestion.application.port.StoreCandidateProvider
import cloud.gearby.api.catalog.candidateingestion.application.service.CandidateIngestionService
import cloud.gearby.api.catalog.candidateingestion.domain.ExternalStoreCandidate
import cloud.gearby.api.catalog.candidateingestion.domain.IngestionRunStatus
import cloud.gearby.api.catalog.candidateingestion.domain.ProviderApprovalStatus
import cloud.gearby.api.catalog.candidateingestion.infrastructure.persistence.entity.CandidateIngestionProviderPolicyEntity
import cloud.gearby.api.catalog.candidateingestion.infrastructure.persistence.entity.CandidateIngestionRunEntity
import cloud.gearby.api.catalog.candidateingestion.infrastructure.persistence.repository.CandidateIngestionProviderPolicyJpaRepository
import cloud.gearby.api.catalog.candidateingestion.infrastructure.persistence.repository.CandidateIngestionRunJpaRepository
import cloud.gearby.api.catalog.candidateingestion.infrastructure.semas.SemasStoreCandidateParser
import cloud.gearby.api.catalog.domain.Category
import cloud.gearby.api.catalog.infrastructure.persistence.repository.StoreJpaRepository
import cloud.gearby.api.support.PostgresIntegrationTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.test.annotation.DirtiesContext
import java.math.BigDecimal
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Tag("integration")
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class CandidateIngestionProviderFlowIntegrationTest
    @Autowired
    constructor(
        private val ingestion: CandidateIngestionService,
        private val policies: CandidateIngestionProviderPolicyJpaRepository,
        private val runs: CandidateIngestionRunJpaRepository,
        private val stores: StoreJpaRepository,
        private val jdbc: NamedParameterJdbcTemplate,
    ) : PostgresIntegrationTest() {
        @BeforeEach
        fun resetMutableData() {
            jdbc.update("DELETE FROM store_candidate_provenance", emptyMap<String, Any>())
            jdbc.update("DELETE FROM candidate_ingestion_runs", emptyMap<String, Any>())
            jdbc.update("DELETE FROM candidate_ingestion_provider_policy", emptyMap<String, Any>())
            jdbc.update("DELETE FROM stores WHERE created_by = 'qa-admin'", emptyMap<String, Any>())
            policies.save(
                CandidateIngestionProviderPolicyEntity(
                    providerKey = "semas",
                    approvalStatus = ProviderApprovalStatus.APPROVED,
                    approvalOwner = "qa-admin",
                    reviewedAt = Instant.parse("2026-01-01T00:00:00Z"),
                    approvedSourceUrl = "https://example.test/approval",
                    allowedFields = "bizesId,bizesNm,rdnmAdr,lon,lat,indsSclsCd,ksicCd",
                    retentionRules = "digest-only",
                    gateVersion = "allow-v1",
                    samplePrecisionResultReference = "sample-v1",
                    sampleSize = 100,
                    regionCount = 5,
                    precisionThreshold = BigDecimal("90.00"),
                    active = true,
                ).apply { createdBy("qa-admin") },
            )
        }

        @Test
        fun `run is persisted before provider call and same terminal key returns without provider call`() {
            val provider =
                FakeProvider {
                    val running =
                        jdbc.queryForObject(
                            "SELECT COUNT(*) FROM candidate_ingestion_runs WHERE status = 'RUNNING'",
                            emptyMap<String, Any>(),
                            Long::class.java,
                        )
                    assertEquals(1, running)
                    ProviderCandidatePage(listOf(candidate("before-network")), hasNext = false)
                }

            val first = ingestion.ingestFromProvider(command("persist-before-network"), provider)
            val second =
                ingestion.ingestFromProvider(
                    command("persist-before-network").copy(allowlistVersion = "", industryCodes = emptyList(), pageSize = 5000),
                    FakeProvider { error("network must not run") },
                )

            assertEquals(IngestionRunStatus.COMPLETED, first.status)
            assertEquals(true, second.idempotent)
            assertEquals(1, provider.calls)
        }

        @Test
        fun `stale running same key is interrupted without provider call`() {
            val policy = policies.findFirstByProviderKeyAndActiveTrueOrderByReviewedAtDescIdAsc("semas")!!
            runs.save(
                CandidateIngestionRunEntity(
                    providerPolicyId = policy.id,
                    providerKey = "semas",
                    idempotencyKey = "stale-key",
                    requestedBy = "qa-admin",
                    status = IngestionRunStatus.RUNNING,
                    gateVersion = "allow-v1",
                ).apply { createdBy("qa-admin") },
            )

            val result = ingestion.ingestFromProvider(command("stale-key"), FakeProvider { error("network must not run") })
            val stale = runs.findByProviderKeyAndIdempotencyKey("semas", "stale-key")!!

            assertEquals(IngestionRunStatus.FAILED, result.status)
            assertEquals("INTERRUPTED", stale.errorCode)
            assertEquals(0, result.seenCount)
        }

        @Test
        fun `cross run duplicate is reviewable from first and later run`() {
            val first =
                ingestion.ingestFromProvider(
                    command("duplicate-first"),
                    FakeProvider {
                        ProviderCandidatePage(listOf(candidate("shared")), false)
                    },
                )
            val second =
                ingestion.ingestFromProvider(
                    command("duplicate-second"),
                    FakeProvider {
                        ProviderCandidatePage(listOf(candidate("shared")), false)
                    },
                )

            assertEquals(IngestionRunStatus.COMPLETED, first.status)
            assertEquals(IngestionRunStatus.COMPLETED, second.status)
            assertEquals(1, first.acceptedCount)
            assertEquals(1, second.dedupedCount)
            assertEquals(listOf("DUPLICATE_SKIPPED"), ingestion.items(first.runId).map { it.outcome.name })
            assertEquals(listOf("DUPLICATE_SKIPPED"), ingestion.items(second.runId).map { it.outcome.name })
        }

        @Test
        fun `quota failure is partial only after evidence committed`() {
            val withEvidence =
                ingestion.ingestFromProvider(
                    command("quota-partial"),
                    FakeProvider { page ->
                        if (page == 1) {
                            ProviderCandidatePage(listOf(candidate("committed")), hasNext = true)
                        } else {
                            throw ProviderFailure(ProviderFailureCategory.QUOTA, "quota exceeded")
                        }
                    },
                )
            val noEvidence =
                ingestion.ingestFromProvider(
                    command("quota-failed"),
                    FakeProvider { throw ProviderFailure(ProviderFailureCategory.QUOTA, "quota exceeded") },
                )

            assertEquals(IngestionRunStatus.PARTIAL, withEvidence.status)
            assertTrue(withEvidence.seenCount > 0)
            assertEquals(IngestionRunStatus.FAILED, noEvidence.status)
        }

        @Test
        fun `malformed SEMAS coordinates fail provider run instead of leaving it running`() {
            val result =
                ingestion.ingestFromProvider(
                    command("malformed-coordinates"),
                    FakeProvider {
                        ProviderCandidatePage(
                            SemasStoreCandidateParser.parse(
                                """
                                {
                                  "bizesId":"bad-coordinates",
                                  "bizesNm":"Bad Coordinates",
                                  "rdnmAdr":"Seoul Road",
                                  "lon":"not-a-number",
                                  "lat":"37.000000",
                                  "indsSclsCd":"209006"
                                }
                                """.trimIndent(),
                                "https://source.test/bad-coordinates",
                            ),
                            hasNext = false,
                        )
                    },
                )
            val run = runs.findByProviderKeyAndIdempotencyKey("semas", "malformed-coordinates")!!

            assertEquals(IngestionRunStatus.FAILED, result.status)
            assertEquals(IngestionRunStatus.FAILED, run.status)
            assertEquals("MALFORMED_RESPONSE", run.errorCode)
            assertEquals("provider response was malformed", run.errorSummary)
        }

        @Test
        fun `out of range SEMAS coordinates fail provider run instead of leaving it running`() {
            val result =
                ingestion.ingestFromProvider(
                    command("out-of-range-coordinates"),
                    FakeProvider {
                        ProviderCandidatePage(
                            SemasStoreCandidateParser.parse(
                                """
                                {
                                  "bizesId":"out-of-range-coordinates",
                                  "bizesNm":"Out Of Range Coordinates",
                                  "rdnmAdr":"Seoul Road",
                                  "lon":"181.000000",
                                  "lat":"91.000000",
                                  "indsSclsCd":"209006"
                                }
                                """.trimIndent(),
                                "https://source.test/out-of-range-coordinates",
                            ),
                            hasNext = false,
                        )
                    },
                )
            val run = runs.findByProviderKeyAndIdempotencyKey("semas", "out-of-range-coordinates")!!

            assertEquals(IngestionRunStatus.FAILED, result.status)
            assertEquals(IngestionRunStatus.FAILED, run.status)
            assertEquals("MALFORMED_RESPONSE", run.errorCode)
            assertEquals("provider response was malformed", run.errorSummary)
        }

        @Test
        fun `categoryless SEMAS candidates are quarantined without draft creation`() {
            val before = stores.count()
            val result =
                ingestion.ingestFromProvider(
                    command("categoryless-quarantine"),
                    FakeProvider { ProviderCandidatePage(listOf(candidate("categoryless", categories = emptySet())), hasNext = false) },
                )

            assertEquals(IngestionRunStatus.COMPLETED, result.status)
            assertEquals(1, result.quarantinedCount)
            assertEquals(before, stores.count())
        }

        @Test
        fun `page limit failure is partial with evidence and failed without evidence`() {
            val partial =
                ingestion.ingestFromProvider(
                    command("page-limit-partial").copy(maxPages = 1),
                    FakeProvider { ProviderCandidatePage(listOf(candidate("limit-evidence")), hasNext = true) },
                )
            val failed =
                ingestion.ingestFromProvider(
                    command("page-limit-failed").copy(maxPages = 1),
                    FakeProvider { ProviderCandidatePage(emptyList(), hasNext = true) },
                )

            assertEquals(IngestionRunStatus.PARTIAL, partial.status)
            assertEquals("PAGE_LIMIT", runs.findByProviderKeyAndIdempotencyKey("semas", "page-limit-partial")!!.errorCode)
            assertEquals(IngestionRunStatus.FAILED, failed.status)
            assertEquals("PAGE_LIMIT", runs.findByProviderKeyAndIdempotencyKey("semas", "page-limit-failed")!!.errorCode)
        }

        @Test
        fun `auth failure before evidence leaves failed run with zero provenance`() {
            val result =
                ingestion.ingestFromProvider(
                    command("auth-failed-zero-evidence"),
                    FakeProvider { throw ProviderFailure(ProviderFailureCategory.AUTH, "auth failed") },
                )

            assertEquals(IngestionRunStatus.FAILED, result.status)
            assertEquals(0, result.seenCount)
            assertEquals(0, ingestion.items(result.runId).size)
            assertEquals("AUTH", runs.findByProviderKeyAndIdempotencyKey("semas", "auth-failed-zero-evidence")!!.errorCode)
        }

        private fun command(key: String) =
            ProviderIngestionCommand(
                providerKey = "semas",
                idempotencyKey = key,
                requestedBy = "qa-admin",
                allowlistVersion = "allow-v1",
                industryCodes = listOf("209006"),
                pageSize = 100,
                maxPages = 3,
            )

        private fun candidate(
            id: String,
            categories: Set<Category> = setOf(Category.HIKING),
        ) = ExternalStoreCandidate(
            providerRecordId = id,
            name = "Trail $id",
            roadAddress = "Seoul Road $id",
            latitude = BigDecimal("37.000000"),
            longitude = BigDecimal("127.000000"),
            sourceUrl = "https://source.test/$id",
            categories = categories,
            payloadSha256Digest = "a".repeat(64),
        )

        private class FakeProvider(
            val response: (Int) -> ProviderCandidatePage,
        ) : StoreCandidateProvider {
            var calls = 0

            override fun fetchPage(
                industryCode: String,
                pageNo: Int,
                pageSize: Int,
            ): ProviderCandidatePage {
                calls += 1
                return response(pageNo)
            }
        }
    }
