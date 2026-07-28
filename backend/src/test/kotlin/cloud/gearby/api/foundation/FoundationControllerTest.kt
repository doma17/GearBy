package cloud.gearby.api.foundation

import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@WebMvcTest(FoundationController::class)
@AutoConfigureMockMvc(addFilters = false)
@Tag("unit")
class FoundationControllerTest(@Autowired private val mockMvc: MockMvc) {
    @Test
    fun `health endpoint is available`() {
        mockMvc.get("/api/v1/health") {
            accept = MediaType.APPLICATION_JSON
        }.andExpect {
            status { isOk() }
            content { contentTypeCompatibleWith("application/json") }
            jsonPath("$.success") { value(true) }
            jsonPath("$.timestamp") { exists() }
            jsonPath("$.data.status") { value("UP") }
        }
    }
}
