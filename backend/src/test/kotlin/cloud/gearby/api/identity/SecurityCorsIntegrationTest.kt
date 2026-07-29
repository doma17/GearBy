package cloud.gearby.api.identity

import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.mock.web.MockHttpServletRequest
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@Tag("unit")
class SecurityCorsIntegrationTest {
    @Test
    fun `cors source allows configured frontend origin with credentials`() {
        val source = SecurityConfig().corsConfigurationSource(CorsProperties())
        val request =
            MockHttpServletRequest(HttpMethod.OPTIONS.name(), "/api/v1/stores").apply {
                addHeader("Origin", "http://localhost:3000")
                addHeader("Access-Control-Request-Method", "GET")
            }

        val configuration = source.getCorsConfiguration(request)

        assertEquals(listOf("http://localhost:3000"), configuration?.allowedOrigins)
        assertEquals(true, configuration?.allowCredentials)
        assertTrue(configuration?.allowedMethods?.containsAll(listOf("GET", "POST", "PATCH", "DELETE", "OPTIONS")) == true)
        assertTrue(configuration.allowedHeaders?.contains("X-XSRF-TOKEN") == true)
    }

    @Test
    fun `cors source trims blanks and rejects wildcard origins`() {
        assertEquals(
            listOf("https://app.example"),
            CorsProperties(allowedOrigins = listOf(" https://app.example ", "", "https://app.example")).normalizedAllowedOrigins(),
        )
        assertFailsWith<IllegalArgumentException> {
            CorsProperties(allowedOrigins = listOf("*")).normalizedAllowedOrigins()
        }
    }
}
