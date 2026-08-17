package cloud.gearby.api.catalog.candidateingestion

import cloud.gearby.api.catalog.application.command.StoreUpsertCommand
import cloud.gearby.api.catalog.application.service.CatalogService
import cloud.gearby.api.catalog.domain.Category
import cloud.gearby.api.catalog.domain.Coordinates
import cloud.gearby.api.catalog.domain.StoreStatus
import cloud.gearby.api.support.PostgresIntegrationTest
import cloud.gearby.api.support.TestAuthentication
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.math.BigDecimal
import java.util.UUID
import kotlin.test.assertEquals

@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
class CandidateIngestionAdminApiIntegrationTest
    @Autowired
    constructor(
        private val mockMvc: MockMvc,
        private val catalog: CatalogService,
        private val jdbc: NamedParameterJdbcTemplate,
    ) : PostgresIntegrationTest() {
        private val admin = TestAuthentication.admin()

        @BeforeEach
        fun resetMutableData() {
            jdbc.update("DELETE FROM store_candidate_provenance", emptyMap<String, Any>())
            jdbc.update("DELETE FROM candidate_ingestion_runs", emptyMap<String, Any>())
            jdbc.update("DELETE FROM candidate_ingestion_provider_policy", emptyMap<String, Any>())
            jdbc.update("DELETE FROM audit_events", emptyMap<String, Any>())
            val seedIds =
                listOf(UUID.fromString("11111111-1111-1111-1111-111111111111"), UUID.fromString("22222222-2222-2222-2222-222222222222"))
            jdbc.update("DELETE FROM store_categories WHERE store_id NOT IN (:seedIds)", mapOf("seedIds" to seedIds))
            jdbc.update("DELETE FROM stores WHERE id NOT IN (:seedIds)", mapOf("seedIds" to seedIds))
        }

        @Test
        fun `admin can list runs and resolve ambiguous item by linking an existing store without publishing`() {
            val existing =
                catalog.create(
                    StoreUpsertCommand(
                        "Existing Link Target",
                        "Seoul",
                        Coordinates(BigDecimal("37.5"), BigDecimal("127.0")),
                        setOf(Category.HIKING),
                    ),
                    "test-admin",
                )
            val (_, runId, itemId) = seedAmbiguousItem()

            mockMvc.get("/api/v1/admin/candidate-ingestion/runs") { with(admin) }.andExpect {
                status { isOk() }
                jsonPath("$.success") { value(true) }
                jsonPath("$.data.total") { value(1) }
                jsonPath("$.data.items[0].id") { value(runId.toString()) }
                jsonPath("$.data.items[0].status") { value("COMPLETED") }
            }
            mockMvc.get("/api/v1/admin/candidate-ingestion/runs/$runId") { with(admin) }.andExpect {
                status { isOk() }
                jsonPath("$.data.id") { value(runId.toString()) }
                jsonPath("$.data.status") { value("COMPLETED") }
                jsonPath("$.data.items") { doesNotExist() }
            }
            mockMvc
                .get("/api/v1/admin/candidate-ingestion/items") {
                    with(admin)
                    param("latestMatchStatus", "AMBIGUOUS")
                    param("latestOutcome", "QUARANTINED")
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.data.items[0].id") { value(itemId.toString()) }
                }

            mockMvc
                .post("/api/v1/admin/candidate-ingestion/items/$itemId/resolve") {
                    with(admin)
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"resolutionType":"LINK_EXISTING","storeId":"${existing.id}"}"""
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.data.itemId") { value(itemId.toString()) }
                    jsonPath("$.data.outcome") { value("RESOLVED") }
                    jsonPath("$.data.matchStatus") { value("RESOLVED_EXISTING") }
                    jsonPath("$.data.resolvedStoreId") { value(existing.id.toString()) }
                    jsonPath("$.data.resolvedStoreStatus") { value("DRAFT") }
                }
            assertEquals(StoreStatus.DRAFT, catalog.find(existing.id)?.status)
        }

        @Test
        fun `candidate resolution reaches public discovery only after an administrator publishes its draft`() {
            val (_, _, itemId) = seedAmbiguousItem("draft-item")

            mockMvc
                .post("/api/v1/admin/candidate-ingestion/items/$itemId/resolve") {
                    with(admin)
                    contentType = MediaType.APPLICATION_JSON
                    content =
                        """
                        {
                          "resolutionType":"CREATE_DRAFT",
                          "name":"Resolved Draft Store",
                          "address":"Busan",
                          "coordinates":{"latitude":35.1,"longitude":129.0},
                          "categories":["CAMPING"],
                          "phone":"010-0000-0000",
                          "hours":"10-18",
                          "description":"admin reviewed"
                        }
                        """.trimIndent()
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.data.outcome") { value("RESOLVED") }
                    jsonPath("$.data.matchStatus") { value("RESOLVED_DRAFT") }
                    jsonPath("$.data.resolvedStoreStatus") { value("DRAFT") }
                }
            val draft = requireNotNull(catalog.findByStatus(StoreStatus.DRAFT).singleOrNull { it.name == "Resolved Draft Store" })
            mockMvc.get("/api/v1/stores/${draft.id}").andExpect { status { isNotFound() } }
            mockMvc.get("/api/v1/stores") { param("q", "Resolved Draft Store") }.andExpect {
                status { isOk() }
                jsonPath("$.data.items.length()") { value(0) }
            }
            mockMvc.post("/api/v1/admin/stores/${draft.id}/review") { with(admin) }.andExpect {
                status { isOk() }
                jsonPath("$.data.status") { value("IN_REVIEW") }
            }
            mockMvc.post("/api/v1/admin/stores/${draft.id}/publish") { with(admin) }.andExpect {
                status { isOk() }
                jsonPath("$.data.status") { value("PUBLISHED") }
            }
            mockMvc.get("/api/v1/stores/${draft.id}").andExpect {
                status { isOk() }
                jsonPath("$.data.name") { value("Resolved Draft Store") }
            }
            mockMvc.get("/api/v1/stores") { param("q", "Resolved Draft Store") }.andExpect {
                status { isOk() }
                jsonPath("$.data.items[0].id") { value(draft.id.toString()) }
            }
        }

        @Test
        fun `admin lists runs and items with database backed pagination totals and filters`() {
            val firstRunId = seedRunWithItems("page-a", 3)
            seedRunWithItems("page-b", 1)

            mockMvc
                .get("/api/v1/admin/candidate-ingestion/runs") {
                    with(admin)
                    param("page", "0")
                    param("size", "1")
                    param("status", "COMPLETED")
                    param("provider", "semas")
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.data.items.length()") { value(1) }
                    jsonPath("$.data.page") { value(0) }
                    jsonPath("$.data.size") { value(1) }
                    jsonPath("$.data.total") { value(2) }
                }
            mockMvc
                .get("/api/v1/admin/candidate-ingestion/items") {
                    with(admin)
                    param("runId", firstRunId.toString())
                    param("latestOutcome", "QUARANTINED")
                    param("latestMatchStatus", "AMBIGUOUS")
                    param("page", "1")
                    param("size", "2")
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.data.items.length()") { value(1) }
                    jsonPath("$.data.page") { value(1) }
                    jsonPath("$.data.size") { value(2) }
                    jsonPath("$.data.total") { value(3) }
                }
        }

        @Test
        fun `run filter returns aggregate candidate for first and last seen runs`() {
            val firstRunId = seedRunWithItems("dedupe-first", 1)
            val secondRunId = seedRun("dedupe-second", seen = 1, deduped = 1)
            jdbc.update(
                """
                UPDATE store_candidate_provenance
                SET last_seen_run_id = :secondRunId,
                    last_seen_at = CURRENT_TIMESTAMP,
                    match_status = 'EXACT_PROVIDER_RECORD',
                    latest_item_outcome = 'DUPLICATE_SKIPPED'
                WHERE first_seen_run_id = :firstRunId
                """.trimIndent(),
                mapOf("firstRunId" to firstRunId, "secondRunId" to secondRunId),
            )

            listOf(firstRunId, secondRunId).forEach { runId ->
                mockMvc
                    .get("/api/v1/admin/candidate-ingestion/items") {
                        with(admin)
                        param("runId", runId.toString())
                    }.andExpect {
                        status { isOk() }
                        jsonPath("$.data.total") { value(1) }
                        jsonPath("$.data.items[0].firstSeenRunId") { value(firstRunId.toString()) }
                        jsonPath("$.data.items[0].lastSeenRunId") { value(secondRunId.toString()) }
                        jsonPath("$.data.items[0].latestOutcome") { value("DUPLICATE_SKIPPED") }
                        jsonPath("$.data.items[0].latestMatchStatus") { value("EXACT_PROVIDER_RECORD") }
                    }
            }
        }

        @Test
        fun `link existing can resolve to a published store without mutating it`() {
            val published = requireNotNull(catalog.find(UUID.fromString("11111111-1111-1111-1111-111111111111")))
            val (_, _, itemId) = seedAmbiguousItem("published-link")

            mockMvc
                .post("/api/v1/admin/candidate-ingestion/items/$itemId/resolve") {
                    with(admin)
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"resolutionType":"LINK_EXISTING","storeId":"${published.id}"}"""
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.data.resolvedStoreId") { value(published.id.toString()) }
                    jsonPath("$.data.resolvedStoreStatus") { value("PUBLISHED") }
                }
            assertEquals(StoreStatus.PUBLISHED, catalog.find(published.id)?.status)
        }

        @Test
        fun `candidate ingestion admin API rejects bad auth invalid enum and already resolved conflicts`() {
            val (_, _, itemId) = seedAmbiguousItem()
            mockMvc.get("/api/v1/admin/candidate-ingestion/runs").andExpect { status { isUnauthorized() } }
            mockMvc.get("/api/v1/admin/candidate-ingestion/runs") { with(TestAuthentication.user()) }.andExpect { status { isForbidden() } }
            mockMvc
                .get("/api/v1/admin/candidate-ingestion/items") {
                    with(admin)
                    param("latestMatchStatus", "BOGUS")
                }.andExpect { status { isBadRequest() } }
            mockMvc.get("/api/v1/admin/candidate-ingestion/runs/${UUID.randomUUID()}") { with(admin) }.andExpect { status { isNotFound() } }

            val draft =
                catalog.create(
                    StoreUpsertCommand(
                        "Draft Link Target",
                        "Seoul",
                        Coordinates(BigDecimal("37.5"), BigDecimal("127.0")),
                        setOf(Category.HIKING),
                    ),
                    "test-admin",
                )
            mockMvc
                .post("/api/v1/admin/candidate-ingestion/items/$itemId/resolve") {
                    with(admin)
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"resolutionType":"LINK_EXISTING","storeId":"${draft.id}"}"""
                }.andExpect { status { isOk() } }
            mockMvc
                .post("/api/v1/admin/candidate-ingestion/items/$itemId/resolve") {
                    with(admin)
                    contentType = MediaType.APPLICATION_JSON
                    content =
                        """
                        {
                          "resolutionType":"CREATE_DRAFT",
                          "name":"Again",
                          "address":"Seoul",
                          "coordinates":{"latitude":37.5,"longitude":127.0},
                          "categories":["HIKING"]
                        }
                        """.trimIndent()
                }.andExpect { status { isConflict() } }
        }

        private fun seedRun(
            key: String,
            seen: Int,
            deduped: Int,
        ): UUID {
            val policyId = UUID.randomUUID()
            val runId = UUID.randomUUID()
            jdbc.update(
                """
                INSERT INTO candidate_ingestion_provider_policy (
                    id, provider_key, approval_status, approval_owner, reviewed_at, approved_source_url,
                    allowed_fields, retention_rules, gate_version, sample_precision_result_reference,
                    sample_size, region_count, precision_threshold, active
                ) VALUES (:policyId, 'semas', 'APPROVED', 'qa-admin', CURRENT_TIMESTAMP, 'https://example.test/approval',
                    'name,address,coordinates', 'digest-only', :gate, 'sample-v1', 100, 5, 90.00, TRUE)
                """.trimIndent(),
                mapOf("policyId" to policyId, "gate" to "gate-$key"),
            )
            jdbc.update(
                """
                INSERT INTO candidate_ingestion_runs (
                    id, provider_policy_id, provider_key, idempotency_key, requested_by, requested_at, started_at,
                    finished_at, status, gate_version, seen_count, accepted_count, deduped_count, quarantined_count, rejected_count, failed_count
                ) VALUES (:runId, :policyId, 'semas', :key, 'qa-admin', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
                    CURRENT_TIMESTAMP, 'COMPLETED', :gate, :seen, 0, :deduped, 0, 0, 0)
                """.trimIndent(),
                mapOf("runId" to runId, "policyId" to policyId, "key" to key, "gate" to "gate-$key", "seen" to seen, "deduped" to deduped),
            )
            return runId
        }

        private fun seedRunWithItems(
            prefix: String,
            itemCount: Int,
        ): UUID {
            val (_, runId, _) = seedAmbiguousItem("$prefix-0")
            (1 until itemCount).forEach { index ->
                val itemId = UUID.randomUUID()
                jdbc.update(
                    """
                    INSERT INTO store_candidate_provenance (
                        id, run_id, provider_key, provider_record_id, dedup_key, first_seen_run_id, last_seen_run_id,
                        first_seen_at, last_seen_at, source_type, source_url, normalized_name, road_address,
                        rounded_latitude, rounded_longitude, match_precedence, match_status, latest_item_outcome,
                        payload_sha256_digest, created_by, edited_by
                    ) VALUES (:itemId, :runId, 'semas', :recordId, :dedupKey, :runId, :runId,
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'API', 'https://example.test/item', 'ambiguous shop', NULL,
                        NULL, NULL, 'AMBIGUOUS', 'AMBIGUOUS', 'QUARANTINED', repeat('a', 64), 'qa-admin', 'qa-admin')
                    """.trimIndent(),
                    mapOf("itemId" to itemId, "runId" to runId, "recordId" to "$prefix-$index", "dedupKey" to "ambiguous-$prefix-$index"),
                )
            }
            return runId
        }

        private fun seedAmbiguousItem(recordId: String = "ambiguous-record"): Triple<UUID, UUID, UUID> {
            val policyId = UUID.randomUUID()
            val runId = UUID.randomUUID()
            val itemId = UUID.randomUUID()
            jdbc.update(
                """
                INSERT INTO candidate_ingestion_provider_policy (
                    id, provider_key, approval_status, approval_owner, reviewed_at, approved_source_url,
                    allowed_fields, retention_rules, gate_version, sample_precision_result_reference,
                    sample_size, region_count, precision_threshold, active
                ) VALUES (:policyId, 'semas', 'APPROVED', 'qa-admin', CURRENT_TIMESTAMP, 'https://example.test/approval',
                    'name,address,coordinates', 'digest-only', 'gate-v1', 'sample-v1', 100, 5, 90.00, TRUE)
                """.trimIndent(),
                mapOf("policyId" to policyId),
            )
            jdbc.update(
                """
                INSERT INTO candidate_ingestion_runs (
                    id, provider_policy_id, provider_key, idempotency_key, requested_by, requested_at, started_at,
                    finished_at, status, gate_version, seen_count, accepted_count, deduped_count, quarantined_count, rejected_count, failed_count
                ) VALUES (:runId, :policyId, 'semas', :key, 'qa-admin', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
                    CURRENT_TIMESTAMP, 'COMPLETED', 'gate-v1', 1, 0, 0, 1, 0, 0)
                """.trimIndent(),
                mapOf("runId" to runId, "policyId" to policyId, "key" to "key-$recordId"),
            )
            jdbc.update(
                """
                INSERT INTO store_candidate_provenance (
                    id, run_id, provider_key, provider_record_id, dedup_key, first_seen_run_id, last_seen_run_id,
                    first_seen_at, last_seen_at, source_type, source_url, normalized_name, road_address,
                    rounded_latitude, rounded_longitude, match_precedence, match_status, latest_item_outcome,
                    payload_sha256_digest, created_by, edited_by
                ) VALUES (:itemId, :runId, 'semas', :recordId, :dedupKey, :runId, :runId,
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'API', 'https://example.test/item', 'ambiguous shop', NULL,
                    NULL, NULL, 'AMBIGUOUS', 'AMBIGUOUS', 'QUARANTINED', repeat('a', 64), 'qa-admin', 'qa-admin')
                """.trimIndent(),
                mapOf("itemId" to itemId, "runId" to runId, "recordId" to recordId, "dedupKey" to "ambiguous-$recordId"),
            )
            return Triple(policyId, runId, itemId)
        }
    }
