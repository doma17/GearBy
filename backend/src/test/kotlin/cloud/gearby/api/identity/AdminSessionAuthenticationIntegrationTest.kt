package cloud.gearby.api.identity

import cloud.gearby.api.support.PostgresIntegrationTest
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import kotlin.test.assertNotNull
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get as getRequest
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post as postRequest

@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(
    properties = [
        "gearby.admin.email=admin@gearby.cloud",
        "gearby.admin.password=test-only-password",
    ],
)
class AdminSessionAuthenticationIntegrationTest
    @Autowired
    constructor(
        private val mockMvc: MockMvc,
    ) : PostgresIntegrationTest() {
        @Test
        fun `configured administrator can create a protected session`() {
            val login =
                mockMvc
                    .post("/api/v1/admin/auth/login") {
                        contentType = MediaType.APPLICATION_JSON
                        content = """{"email":"admin@gearby.cloud","password":"test-only-password"}"""
                    }.andExpect {
                        status { isOk() }
                        jsonPath("$.success") { value(true) }
                        jsonPath("$.data.authenticated") { value(true) }
                        jsonPath("$.data.email") { value("admin@gearby.cloud") }
                    }.andReturn()
            val session = assertNotNull(login.request.getSession(false)) as MockHttpSession

            mockMvc
                .perform(getRequest("/api/v1/admin/dashboard").session(session))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.success").value(true))

            mockMvc
                .perform(postRequest("/api/v1/admin/auth/logout").session(session))
                .andExpect(status().isForbidden)
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"))

            val csrfCookie = assertNotNull(login.response.getCookie("XSRF-TOKEN"))
            val csrfToken = Regex("""\"csrfToken\":\"([^\"]+)\"""").find(login.response.contentAsString)?.groupValues?.get(1)
            assertNotNull(csrfToken)
            mockMvc
                .perform(
                    postRequest("/api/v1/admin/auth/logout")
                        .session(session)
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfToken),
                ).andExpect(status().isOk)
        }

        @Test
        fun `invalid credentials never create an administrator session`() {
            mockMvc
                .post("/api/v1/admin/auth/login") {
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"email":"admin@gearby.cloud","password":"wrong-password"}"""
                }.andExpect {
                    status { isUnauthorized() }
                    jsonPath("$.success") { value(false) }
                    jsonPath("$.error.code") { value("UNAUTHORIZED") }
                }
        }
    }
