package cloud.gearby.api.identity.api.request

data class AdminLoginRequest(
    val email: String = "",
    val password: String = "",
)
