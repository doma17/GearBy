package cloud.gearby.api.identity

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("gearby.oidc")
data class OidcProperties(val issuerUri: String = "", val audience: String = "") {
    fun configured() = issuerUri.isNotBlank() && audience.isNotBlank()
}
