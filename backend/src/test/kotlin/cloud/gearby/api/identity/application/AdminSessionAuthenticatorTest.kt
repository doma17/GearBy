package cloud.gearby.api.identity.application

import cloud.gearby.api.identity.AdminSessionProperties
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import kotlin.test.assertNull

@Tag("unit")
class AdminSessionAuthenticatorTest {
    @Test
    fun `locks the single administrator after five failed attempts`() {
        val encoder = BCryptPasswordEncoder()
        val authenticator =
            AdminSessionAuthenticator(
                AdminSessionProperties("admin@gearby.cloud", requireNotNull(encoder.encode("correct-password"))),
                encoder,
            )

        repeat(5) { assertNull(authenticator.authenticate("admin@gearby.cloud", "wrong-password")) }

        assertNull(authenticator.authenticate("admin@gearby.cloud", "correct-password"))
    }
}
