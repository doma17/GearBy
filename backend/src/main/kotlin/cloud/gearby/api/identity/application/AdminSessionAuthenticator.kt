package cloud.gearby.api.identity.application

import cloud.gearby.api.identity.AdminSessionProperties
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.stereotype.Service
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

@Service
class AdminSessionAuthenticator(
    private val properties: AdminSessionProperties,
) {
    // A single environment-managed account keeps the MVP fail-closed until an identity provider is adopted.
    fun authenticate(
        email: String,
        password: String,
    ) = if (
        properties.configured() &&
        email.trim().equals(properties.email.trim(), ignoreCase = true) &&
        MessageDigest.isEqual(password.toByteArray(StandardCharsets.UTF_8), properties.password.toByteArray(StandardCharsets.UTF_8))
    ) {
        UsernamePasswordAuthenticationToken.authenticated(properties.email.trim(), null, listOf(SimpleGrantedAuthority("ADMIN")))
    } else {
        null
    }
}
