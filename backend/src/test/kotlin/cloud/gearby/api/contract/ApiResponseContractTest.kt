package cloud.gearby.api.contract

import cloud.gearby.api.support.PostgresIntegrationTest
import cloud.gearby.api.support.TestAuthentication
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.yaml.snakeyaml.Yaml
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** Verifies that runtime responses keep the documented public and admin boundaries. */
@Tag("contract")
@SpringBootTest
@AutoConfigureMockMvc
class ApiResponseContractTest
    @Autowired
    constructor(
        private val mockMvc: MockMvc,
    ) : PostgresIntegrationTest() {
        @Test
        fun `public endpoints follow the documented success envelope`() {
            mockMvc.get("/api/v1/health").andExpect {
                status { isOk() }
                jsonPath("$.success") { value(true) }
                jsonPath("$.timestamp") { exists() }
                jsonPath("$.data.status") { value("UP") }
                jsonPath("$.error") { value(null) }
            }
            mockMvc.get("/api/v1/categories").andExpect {
                status { isOk() }
                jsonPath("$.success") { value(true) }
                jsonPath("$.data.length()") { value(4) }
                jsonPath("$.error") { value(null) }
            }
            mockMvc
                .post("/api/v1/feedback") {
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"kind":"GENERAL","content":"Contract feedback"}"""
                }.andExpect {
                    status { isAccepted() }
                    jsonPath("$.success") { value(true) }
                    jsonPath("$.data.status") { value("ACCEPTED") }
                    jsonPath("$.error") { value(null) }
                }
        }

        @Test
        fun `admin authentication failures follow the documented error envelope`() {
            mockMvc.get("/api/v1/admin/dashboard").andExpect {
                status { isUnauthorized() }
                jsonPath("$.success") { value(false) }
                jsonPath("$.data") { value(null) }
                jsonPath("$.error.code") { value("UNAUTHORIZED") }
            }
            mockMvc.get("/api/v1/admin/dashboard") { with(TestAuthentication.user()) }.andExpect {
                status { isForbidden() }
                jsonPath("$.success") { value(false) }
                jsonPath("$.error.code") { value("FORBIDDEN") }
            }
            mockMvc.get("/api/v1/admin/dashboard") { with(TestAuthentication.admin()) }.andExpect {
                status { isOk() }
                jsonPath("$.success") { value(true) }
                jsonPath("$.data") { exists() }
            }
        }

        @Test
        fun `OpenAPI separates public freshness from admin lifecycle semantics`() {
            val openApi = Yaml().load<Map<String, Any?>>(Path.of("..", "contracts", "openapi.yaml").readText())
            val schemas = openApi.node("components", "schemas")
            val applyCorrection = openApi.node("components", "parameters", "ApplyCorrection", "schema")
            val publicStore = schemas.node("Store")
            val adminStore = schemas.node("AdminStore")
            val adminProperties = adminStore.node("properties")

            assertEquals(true, applyCorrection["default"])
            assertEquals(
                listOf("id", "name", "address", "coordinates", "categories", "verifiedAt", "informationStatus"),
                publicStore["required"],
            )
            assertFalse("allOf" in adminStore)
            assertEquals(listOf("id", "name", "address", "coordinates", "categories", "status"), adminStore["required"])
            assertEquals(listOf("string", "null"), adminProperties.node("verifiedAt")["type"])
            assertEquals(listOf("string", "null"), adminProperties.node("informationStatus")["type"])
            assertEquals(listOf("VERIFIED", "REVIEW_DUE", null), adminProperties.node("informationStatus")["enum"])
        }

        @Test
        fun `OpenAPI documents candidate ingestion admin paths and exact enums`() {
            val openApi = Yaml().load<Map<String, Any?>>(Path.of("..", "contracts", "openapi.yaml").readText())
            val paths = openApi.node("paths")
            val schemas = openApi.node("components", "schemas")
            val responses = openApi.node("components", "responses")

            assertEquals(true, "/admin/candidate-ingestion/runs" in paths)
            assertEquals(true, "/admin/candidate-ingestion/runs/{runId}" in paths)
            assertEquals(true, "/admin/candidate-ingestion/items" in paths)
            assertEquals(true, "/admin/candidate-ingestion/items/{itemId}/resolve" in paths)
            assertEquals(
                "#/components/responses/ListCandidateIngestionRunsSuccess",
                paths.node("/admin/candidate-ingestion/runs", "get", "responses", "200")["\$ref"],
            )
            assertEquals(
                "#/components/responses/GetCandidateIngestionRunSuccess",
                paths.node("/admin/candidate-ingestion/runs/{runId}", "get", "responses", "200")["\$ref"],
            )
            assertEquals(
                "#/components/responses/ListCandidateIngestionItemsSuccess",
                paths.node("/admin/candidate-ingestion/items", "get", "responses", "200")["\$ref"],
            )
            assertEquals(
                "#/components/responses/ResolveCandidateIngestionItemSuccess",
                paths.node("/admin/candidate-ingestion/items/{itemId}/resolve", "post", "responses", "200")["\$ref"],
            )
            assertEquals(
                "#/components/schemas/CandidateRun",
                schemas.node("GetCandidateIngestionRunSuccessEnvelope", "properties", "data")["\$ref"],
            )
            assertEquals(listOf("RUNNING", "PARTIAL", "FAILED", "COMPLETED"), schemas.node("CandidateIngestionRunStatus")["enum"])
            assertEquals(
                listOf(
                    "NOT_EVALUATED",
                    "NO_MATCH",
                    "EXACT_PROVIDER_RECORD",
                    "EXACT_NAME_ADDRESS",
                    "EXACT_NAME_COORDINATES",
                    "AMBIGUOUS",
                    "RESOLVED_EXISTING",
                    "RESOLVED_DRAFT",
                ),
                schemas.node("CandidateMatchStatus")["enum"],
            )
            assertEquals(
                listOf(
                    "DRAFT_CREATED",
                    "MATCHED_EXISTING",
                    "DUPLICATE_SKIPPED",
                    "QUARANTINED",
                    "BLOCKED_BY_GATE",
                    "REJECTED",
                    "ITEM_FAILED",
                    "RESOLVED",
                ),
                schemas.node("CandidateItemOutcome")["enum"],
            )

            assertAdminSecurity(paths.node("/admin/candidate-ingestion/runs", "get"))
            assertAdminSecurity(paths.node("/admin/candidate-ingestion/runs/{runId}", "get"))
            assertAdminSecurity(paths.node("/admin/candidate-ingestion/items", "get"))
            val resolveOperation = paths.node("/admin/candidate-ingestion/items/{itemId}/resolve", "post")
            assertAdminSecurity(resolveOperation)
            assertEquals("#/components/parameters/CsrfToken", (resolveOperation["parameters"] as List<*>)[1].node()["\$ref"])
            assertEquals(
                "#/components/schemas/GetAdminSessionSuccessEnvelope",
                responses.node("GetAdminSessionSuccess", "content", "application/json", "schema")["\$ref"],
            )

            assertEquals(
                listOf(
                    "id",
                    "firstSeenRunId",
                    "lastSeenRunId",
                    "provider",
                    "sourceUrl",
                    "normalizedName",
                    "latestOutcome",
                    "latestMatchStatus",
                    "createdAt",
                    "updatedAt",
                ),
                schemas.node("CandidateItem")["required"],
            )
        }
    }

@Suppress("UNCHECKED_CAST")
private fun Map<String, Any?>.node(vararg path: String): Map<String, Any?> =
    path.fold(this) { node, key -> node.getValue(key) as Map<String, Any?> }

@Suppress("UNCHECKED_CAST")
private fun Any?.node(): Map<String, Any?> = this as Map<String, Any?>

private fun assertAdminSecurity(operation: Map<String, Any?>) {
    assertEquals(
        listOf(mapOf("adminSession" to emptyList<Any>()), mapOf("adminOidc" to listOf("ADMIN"))),
        operation["security"],
    )
    val responses = operation.node("responses")
    listOf("401", "403").forEach { status ->
        assertEquals("#/components/responses/ApiError", responses.node(status)["\$ref"])
    }
}
