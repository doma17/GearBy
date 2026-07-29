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
    }

@Suppress("UNCHECKED_CAST")
private fun Map<String, Any?>.node(vararg path: String): Map<String, Any?> =
    path.fold(this) { node, key -> node.getValue(key) as Map<String, Any?> }
