package cloud.gearby.api.identity.api.response

data class AdminSessionResponse(
    val authenticated: Boolean,
    val email: String? = null,
    val csrfToken: String,
)
