package cloud.gearby.api.identity

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("gearby.admin")
data class AdminSessionProperties(
    val email: String = "",
    val passwordHash: String = "",
) {
    fun configured() = email.isNotBlank() && passwordHash.isNotBlank()

    override fun toString() = "AdminSessionProperties(email=$email, passwordHash=****)"
}
