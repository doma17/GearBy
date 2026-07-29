package cloud.gearby.api.catalog.candidateingestion.application

import cloud.gearby.api.catalog.candidateingestion.application.command.CandidateIngestionCommand
import cloud.gearby.api.catalog.candidateingestion.application.service.CandidateIngestionService
import cloud.gearby.api.catalog.candidateingestion.domain.CandidateItemOutcome
import cloud.gearby.api.catalog.candidateingestion.domain.CandidateMatchStatus
import cloud.gearby.api.catalog.candidateingestion.domain.ExternalStoreCandidate
import cloud.gearby.api.catalog.candidateingestion.domain.IngestionRunStatus
import cloud.gearby.api.catalog.candidateingestion.domain.ProviderApprovalStatus
import cloud.gearby.api.catalog.candidateingestion.infrastructure.persistence.entity.CandidateIngestionProviderPolicyEntity
import cloud.gearby.api.catalog.candidateingestion.infrastructure.persistence.repository.CandidateIngestionProviderPolicyJpaRepository
import cloud.gearby.api.catalog.domain.Category
import cloud.gearby.api.catalog.domain.StoreStatus
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
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@Tag("integration")
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class CandidateIngestionServiceIntegrationTest
    @Autowired
    constructor(
        private val ingestion: CandidateIngestionService,
        private val policies: CandidateIngestionProviderPolicyJpaRepository,
        private val stores: StoreJpaRepository,
        private val jdbc: NamedParameterJdbcTemplate,
    ) : PostgresIntegrationTest() {
        @BeforeEach
        fun resetMutableData() {
            jdbc.update("DELETE FROM store_candidate_provenance", emptyMap<String, Any>())
            jdbc.update("DELETE FROM candidate_ingestion_runs", emptyMap<String, Any>())
            jdbc.update("DELETE FROM candidate_ingestion_provider_policy", emptyMap<String, Any>())
            val seedIds =
                listOf(UUID.fromString("11111111-1111-1111-1111-111111111111"), UUID.fromString("22222222-2222-2222-2222-222222222222"))
            jdbc.update("DELETE FROM audit_events WHERE resource_id NOT IN (:seedIds)", mapOf("seedIds" to seedIds))
            jdbc.update("DELETE FROM store_categories WHERE store_id NOT IN (:seedIds)", mapOf("seedIds" to seedIds))
            jdbc.update("DELETE FROM stores WHERE id NOT IN (:seedIds)", mapOf("seedIds" to seedIds))
            policies.save(
                CandidateIngestionProviderPolicyEntity(
                    providerKey = "semas",
                    approvalStatus = ProviderApprovalStatus.APPROVED,
                    approvalOwner = "qa-admin",
                    reviewedAt = Instant.parse("2026-01-01T00:00:00Z"),
                    approvedSourceUrl = "https://example.test/approval",
                    allowedFields = "name,address,coordinates,phone,industryCode",
                    retentionRules = "digest-only",
                    gateVersion = "gate-v1",
                    samplePrecisionResultReference = "sample-v1",
                    sampleSize = 100,
                    regionCount = 5,
                    precisionThreshold = BigDecimal("90.00"),
                    active = true,
                ).apply { createdBy("qa-admin") },
            )
        }

        @Test
        fun `ingests new candidates as draft only and same key rerun is a no-op`() {
            val before = stores.count()
            val command =
                CandidateIngestionCommand(
                    providerKey = "semas",
                    idempotencyKey = "run-draft-idempotent",
                    requestedBy = "qa-admin",
                    candidates = listOf(candidate("semas-1", "New Trail Shop")),
                )

            val first = ingestion.ingest(command)
            val second = ingestion.ingest(command)

            assertEquals(IngestionRunStatus.COMPLETED, first.status)
            assertEquals(false, first.idempotent)
            assertEquals(true, second.idempotent)
            assertEquals(first.runId, second.runId)
            assertEquals(before + 1, stores.count())
            val created = stores.findAll().single { it.name == "new trail shop" }
            assertEquals(StoreStatus.DRAFT, created.status)
            assertEquals(created.id, ingestion.items(first.runId).single().resolvedStoreId)
        }

        @Test
        fun `updates stable provenance for duplicate provider record without creating another store`() {
            val command =
                CandidateIngestionCommand(
                    providerKey = "semas",
                    idempotencyKey = "run-duplicate-record",
                    requestedBy = "qa-admin",
                    candidates = listOf(candidate("semas-dup", "Duplicate Trail"), candidate("semas-dup", "Duplicate Trail")),
                )

            val result = ingestion.ingest(command)
            val items = ingestion.items(result.runId)

            assertEquals(2, result.seenCount)
            assertEquals(1, result.acceptedCount)
            assertEquals(1, result.dedupedCount)
            assertEquals(1, items.size)
            assertEquals(CandidateItemOutcome.DUPLICATE_SKIPPED, items.single().outcome)
            assertEquals(CandidateMatchStatus.EXACT_PROVIDER_RECORD, items.single().matchStatus)
            assertEquals(1, stores.findAll().count { it.name == "duplicate trail" })
        }

        @Test
        fun `matches existing stores and quarantines ambiguous candidates without draft creation`() {
            val before = stores.count()
            val command =
                CandidateIngestionCommand(
                    providerKey = "semas",
                    idempotencyKey = "run-match-and-ambiguous",
                    requestedBy = "qa-admin",
                    candidates =
                        listOf(
                            candidate("semas-existing", "GearBy Seoul Trail", "서울특별시 종로구 세종대로 1"),
                            ExternalStoreCandidate(
                                name = "Mystery Gear",
                                sourceUrl = "https://example.test/mystery",
                                categories = setOf(Category.HIKING),
                                payloadSha256Digest = "c".repeat(64),
                            ),
                        ),
                )

            val result = ingestion.ingest(command)
            val outcomes = ingestion.items(result.runId).associate { it.matchStatus to it.outcome }

            assertEquals(before, stores.count())
            assertEquals(CandidateItemOutcome.MATCHED_EXISTING, outcomes[CandidateMatchStatus.EXACT_NAME_ADDRESS])
            assertEquals(CandidateItemOutcome.QUARANTINED, outcomes[CandidateMatchStatus.AMBIGUOUS])
            assertNotNull(
                ingestion.items(result.runId).single { it.matchStatus == CandidateMatchStatus.EXACT_NAME_ADDRESS }.resolvedStoreId,
            )
        }

        private fun candidate(
            recordId: String,
            name: String,
            address: String = "Seoul Test Road 1",
        ) = ExternalStoreCandidate(
            providerRecordId = recordId,
            name = name,
            roadAddress = address,
            latitude = BigDecimal("37.500000"),
            longitude = BigDecimal("127.000000"),
            sourceUrl = "https://example.test/$recordId",
            categories = setOf(Category.HIKING),
            payloadSha256Digest = "b".repeat(64),
        )
    }
