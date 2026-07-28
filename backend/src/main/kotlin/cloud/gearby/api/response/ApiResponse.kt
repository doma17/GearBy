package cloud.gearby.api.response

import java.time.Instant

data class ApiResponse<T>(
    val success: Boolean,
    val timestamp: Instant,
    val data: T?,
    val error: ApiError?,
) {
    companion object {
        // Every REST outcome is created here to keep success and failure envelopes symmetric.
        fun <T> success(data: T): ApiResponse<T> = ApiResponse(true, Instant.now(), data, null)

        fun success(): ApiResponse<Nothing> = ApiResponse(true, Instant.now(), null, null)

        fun failure(code: ApiErrorCode, message: String): ApiResponse<Nothing> =
            ApiResponse(false, Instant.now(), null, ApiError(code, message))
    }
}
