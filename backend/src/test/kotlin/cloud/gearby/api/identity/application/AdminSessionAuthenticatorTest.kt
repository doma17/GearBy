package cloud.gearby.api.identity.application

import cloud.gearby.api.identity.AdminSessionProperties
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import java.util.UUID
import kotlin.test.assertNull

@Tag("unit")
class AdminSessionAuthenticatorTest {
    @Test
    fun `locks the single administrator after five failed attempts`() {
        val encoder = BCryptPasswordEncoder()
        val acceptedValue = UUID.randomUUID().toString()
        val rejectedValue = UUID.randomUUID().toString()
        val authenticator =
            AdminSessionAuthenticator(
                AdminSessionProperties("admin@gearby.cloud", requireNotNull(encoder.encode(acceptedValue))),
                encoder,
            )

        repeat(5) { assertNull(authenticator.authenticate("admin@gearby.cloud", rejectedValue, "127.0.0.1")) }

        assertNull(authenticator.authenticate("admin@gearby.cloud", acceptedValue, "127.0.0.1"))
        kotlin.test.assertNotNull(authenticator.authenticate("admin@gearby.cloud", acceptedValue, "127.0.0.2"))
        repeat(4) { assertNull(authenticator.authenticate("admin@gearby.cloud", rejectedValue, "127.0.0.2")) }
        kotlin.test.assertNotNull(authenticator.authenticate("admin@gearby.cloud", acceptedValue, "127.0.0.2"))
        repeat(4) { assertNull(authenticator.authenticate("admin@gearby.cloud", rejectedValue, "127.0.0.2")) }
        kotlin.test.assertNotNull(authenticator.authenticate("admin@gearby.cloud", acceptedValue, "127.0.0.2"))
    }
}
