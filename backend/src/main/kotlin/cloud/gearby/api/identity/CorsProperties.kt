package cloud.gearby.api.identity

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("gearby.cors")
data class CorsProperties(
    val allowedOrigins: List<String> = listOf("http://localhost:3000"),
    val allowedMethods: List<String> = listOf("GET", "POST", "PATCH", "DELETE", "OPTIONS"),
    val allowedHeaders: List<String> = listOf("Authorization", "Content-Type", "Accept"),
    val allowCredentials: Boolean = true,
    val maxAge: Long = 3600,
) {
    fun normalizedAllowedOrigins(): List<String> {
        require(
            allowedOrigins.none { it.trim() == "*" },
        ) { "gearby.cors.allowed-origins must be explicit; wildcard origins are not allowed" }
        return allowedOrigins.map { it.trim() }.filter { it.isNotBlank() }.distinct()
    }
}
