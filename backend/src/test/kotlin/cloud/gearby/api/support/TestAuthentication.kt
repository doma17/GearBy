package cloud.gearby.api.support

import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.web.servlet.request.RequestPostProcessor

object TestAuthentication {
    fun admin(): RequestPostProcessor = jwt().authorities(SimpleGrantedAuthority("ADMIN"))
    fun user(): RequestPostProcessor = jwt()
}
