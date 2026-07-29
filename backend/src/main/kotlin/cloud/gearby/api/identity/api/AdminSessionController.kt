package cloud.gearby.api.identity.api

import cloud.gearby.api.identity.api.request.AdminLoginRequest
import cloud.gearby.api.identity.api.response.AdminSessionResponse
import cloud.gearby.api.identity.application.AdminSessionAuthenticator
import cloud.gearby.api.response.ApiResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler
import org.springframework.security.web.context.SecurityContextRepository
import org.springframework.security.web.csrf.CsrfToken
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

@RestController
@RequestMapping("/api/v1/admin/auth")
class AdminSessionController(
    private val authenticator: AdminSessionAuthenticator,
    private val securityContextRepository: SecurityContextRepository,
) {
    @GetMapping("/session")
    fun session(
        authentication: Authentication?,
        csrfToken: CsrfToken,
    ): ApiResponse<AdminSessionResponse> =
        ApiResponse.success(
            AdminSessionResponse(
                authenticated = authentication?.authorities?.any { it.authority == "ADMIN" } == true,
                email = authentication?.takeIf { it.authorities.any { authority -> authority.authority == "ADMIN" } }?.name,
                csrfToken = csrfToken.token,
            ),
        )

    @PostMapping("/login")
    fun login(
        @RequestBody request: AdminLoginRequest,
        servletRequest: HttpServletRequest,
        servletResponse: HttpServletResponse,
        csrfToken: CsrfToken,
    ): ApiResponse<AdminSessionResponse> {
        val authentication =
            authenticator.authenticate(request.email, request.password)
                ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid email or password")
        val context = SecurityContextHolder.createEmptyContext().apply { this.authentication = authentication }
        securityContextRepository.saveContext(context, servletRequest, servletResponse)
        return ApiResponse.success(AdminSessionResponse(authenticated = true, email = authentication.name, csrfToken = csrfToken.token))
    }

    @PostMapping("/logout")
    fun logout(
        servletRequest: HttpServletRequest,
        servletResponse: HttpServletResponse,
        csrfToken: CsrfToken,
    ): ApiResponse<AdminSessionResponse> {
        SecurityContextLogoutHandler().logout(servletRequest, servletResponse, SecurityContextHolder.getContext().authentication)
        return ApiResponse.success(AdminSessionResponse(authenticated = false, csrfToken = csrfToken.token))
    }
}
