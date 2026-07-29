package cloud.gearby.api.identity

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("gearby.admin")
data class AdminSessionProperties(
    val email: String = "",
    val password: String = "",
) {
    fun configured() = email.isNotBlank() && password.isNotBlank()
}
