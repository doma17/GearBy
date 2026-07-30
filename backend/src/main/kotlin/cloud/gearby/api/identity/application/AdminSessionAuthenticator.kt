package cloud.gearby.api.identity.application

import cloud.gearby.api.identity.AdminSessionProperties
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

@Service
class AdminSessionAuthenticator(
    private val properties: AdminSessionProperties,
    private val passwordEncoder: PasswordEncoder,
) {
    private val failedAttempts = ConcurrentHashMap<String, FailedLogin>()

    // A single environment-managed account keeps the MVP fail-closed until an identity provider is adopted.
    fun authenticate(
        email: String,
        password: String,
        source: String,
    ): Authentication? {
        val now = Instant.now()
        if (!properties.configured() || failedAttempts[source]?.lockedUntil?.isAfter(now) == true) return null
        if (email.trim().equals(properties.email.trim(), ignoreCase = true) && passwordEncoder.matches(password, properties.passwordHash)) {
            failedAttempts.remove(source)
            return UsernamePasswordAuthenticationToken.authenticated(properties.email.trim(), null, listOf(SimpleGrantedAuthority("ADMIN")))
        }
        // ponytail: local throttling resets on restart; move this control to the IdP or gateway before multi-node administration.
        failedAttempts.compute(source) { _, previous ->
            val failures =
                if (previous?.lockedUntil != null && !previous.lockedUntil.isAfter(now)) 0 else previous?.failures ?: 0
            val nextFailures = failures + 1
            FailedLogin(nextFailures, if (nextFailures >= MAX_FAILURES) now.plus(LOCK_DURATION) else null)
        }
        return null
    }

    private data class FailedLogin(
        val failures: Int,
        val lockedUntil: Instant?,
    )

    private companion object {
        const val MAX_FAILURES = 5
        val LOCK_DURATION: Duration = Duration.ofMinutes(15)
    }
}
