package cloud.gearby.api.identity

import cloud.gearby.api.response.ApiErrorCode
import cloud.gearby.api.response.ApiResponse
import jakarta.servlet.http.HttpServletResponse
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.security.oauth2.core.OAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtValidators
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.access.AccessDeniedHandler
import org.springframework.security.web.context.HttpSessionSecurityContextRepository
import org.springframework.security.web.context.SecurityContextRepository
import org.springframework.security.web.csrf.CookieCsrfTokenRepository
import org.springframework.security.web.csrf.CsrfTokenRepository
import org.springframework.security.web.util.matcher.RequestMatcher
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource
import tools.jackson.databind.ObjectMapper

@Configuration
@EnableConfigurationProperties(OidcProperties::class, CorsProperties::class, AdminSessionProperties::class)
class SecurityConfig {
    @Bean
    fun securityFilterChain(
        http: HttpSecurity,
        properties: OidcProperties,
        corsConfigurationSource: CorsConfigurationSource,
        securityContextRepository: SecurityContextRepository,
        csrfTokenRepository: CsrfTokenRepository,
        objectMapper: ObjectMapper,
    ): SecurityFilterChain {
        // Authentication failures must preserve the public API error contract instead of returning HTML.
        val authenticationEntryPoint =
            AuthenticationEntryPoint { _, response, _ ->
                writeError(response, HttpStatus.UNAUTHORIZED, ApiErrorCode.UNAUTHORIZED, "authentication is required", objectMapper)
            }
        val accessDeniedHandler =
            AccessDeniedHandler { _, response, _ ->
                writeError(response, HttpStatus.FORBIDDEN, ApiErrorCode.FORBIDDEN, "access is forbidden", objectMapper)
            }

        http
            .cors { it.configurationSource(corsConfigurationSource) }
            .csrf {
                it.csrfTokenRepository(csrfTokenRepository)
                it.requireCsrfProtectionMatcher(sessionCsrfMatcher())
            }.securityContext { it.securityContextRepository(securityContextRepository) }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED) }
            .authorizeHttpRequests {
                it
                    .requestMatchers("/api/v1/health", "/actuator/health", "/error")
                    .permitAll()
                    .requestMatchers("/api/v1/admin/auth/session", "/api/v1/admin/auth/login")
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/v1/categories", "/api/v1/stores", "/api/v1/stores/**")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/v1/feedback")
                    .permitAll()
                    .requestMatchers("/api/v1/admin/**")
                    .hasAuthority("ADMIN")
                    .anyRequest()
                    .denyAll()
            }.exceptionHandling {
                it
                    .authenticationEntryPoint(authenticationEntryPoint)
                    .accessDeniedHandler(accessDeniedHandler)
            }
        // Local development fails closed for admin routes until a real OIDC issuer and audience are configured.
        if (properties.configured()) {
            http.oauth2ResourceServer { resource ->
                resource.authenticationEntryPoint(authenticationEntryPoint)
                resource.accessDeniedHandler(accessDeniedHandler)
                resource.jwt { jwt -> jwt.decoder(jwtDecoder(properties)).jwtAuthenticationConverter(jwtAuthenticationConverter()) }
            }
        }
        return http.build()
    }

    @Bean
    fun securityContextRepository(): SecurityContextRepository = HttpSessionSecurityContextRepository()

    @Bean
    fun csrfTokenRepository(): CsrfTokenRepository = CookieCsrfTokenRepository.withHttpOnlyFalse()

    @Bean
    fun corsConfigurationSource(properties: CorsProperties): CorsConfigurationSource {
        val configuration =
            CorsConfiguration().apply {
                allowedOrigins = properties.normalizedAllowedOrigins()
                allowedMethods = properties.allowedMethods
                allowedHeaders = properties.allowedHeaders
                allowCredentials = properties.allowCredentials
                maxAge = properties.maxAge
            }
        return UrlBasedCorsConfigurationSource().apply { registerCorsConfiguration("/**", configuration) }
    }

    private fun sessionCsrfMatcher() =
        RequestMatcher { request ->
            request.method !in setOf(HttpMethod.GET.name(), HttpMethod.HEAD.name(), HttpMethod.OPTIONS.name(), HttpMethod.TRACE.name()) &&
                request.getSession(false)?.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY) != null
        }

    private fun jwtDecoder(properties: OidcProperties): JwtDecoder =
        NimbusJwtDecoder.withIssuerLocation(properties.issuerUri).build().also { decoder ->
            val audience =
                OAuth2TokenValidator<Jwt> { jwt ->
                    if (properties.audience in (jwt.audience ?: emptyList())) {
                        OAuth2TokenValidatorResult.success()
                    } else {
                        OAuth2TokenValidatorResult.failure(OAuth2Error("invalid_token", "JWT audience is not accepted", null))
                    }
                }
            decoder.setJwtValidator(DelegatingOAuth2TokenValidator(JwtValidators.createDefaultWithIssuer(properties.issuerUri), audience))
        }

    private fun jwtAuthenticationConverter() =
        JwtAuthenticationConverter().apply {
            setJwtGrantedAuthoritiesConverter { jwt ->
                (jwt.getClaimAsStringList("roles") ?: emptyList()).filter { it == "ADMIN" }.map(::SimpleGrantedAuthority)
            }
        }

    private fun writeError(
        response: HttpServletResponse,
        status: HttpStatus,
        code: ApiErrorCode,
        message: String,
        objectMapper: ObjectMapper,
    ) {
        response.status = status.value()
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        objectMapper.writeValue(response.outputStream, ApiResponse.failure(code, message))
    }
}
