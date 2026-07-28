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
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.assertContains
import kotlin.test.assertFalse

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
            val openApi = Path.of("..", "contracts", "openapi.yaml").readText()
            val applyCorrection = openApi.substringAfter("    ApplyCorrection:\n").substringBefore("    Bbox:")
            val publicStore = openApi.substringAfter("    Store:\n").substringBefore("    StoreInformationStatus:")
            val adminStore = openApi.substringAfter("    AdminStore:\n").substringBefore("    CategoryHealth:")

            assertContains(applyCorrection, "default: true")
            assertContains(publicStore, "required: [id, name, address, coordinates, categories, verifiedAt, informationStatus]")
            assertFalse(adminStore.contains("allOf:"))
            assertContains(adminStore, "required: [id, name, address, coordinates, categories, status]")
            assertContains(adminStore, "verifiedAt: { type: string, format: date-time, nullable: true }")
            assertContains(adminStore, "informationStatus:")
            assertContains(adminStore, "nullable: true")
        }
    }
