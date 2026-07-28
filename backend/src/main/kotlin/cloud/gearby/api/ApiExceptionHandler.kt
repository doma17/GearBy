package cloud.gearby.api

import cloud.gearby.api.response.ApiErrorCode
import cloud.gearby.api.response.ApiResponse
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.server.ResponseStatusException

@RestControllerAdvice
class ApiExceptionHandler {
    // Convert framework and domain failures into the same envelope used by successful responses.
    @ExceptionHandler(IllegalArgumentException::class, HttpMessageNotReadableException::class)
    fun invalidRequest(error: Exception): ResponseEntity<ApiResponse<Nothing>> =
        failure(HttpStatus.BAD_REQUEST, ApiErrorCode.INVALID_REQUEST, error.message ?: "invalid request")

    @ExceptionHandler(ResponseStatusException::class)
    fun responseStatus(error: ResponseStatusException): ResponseEntity<ApiResponse<Nothing>> =
        failure(error.statusCode.value(), error.reason ?: "request failed")

    @ExceptionHandler(Exception::class)
    fun unexpected(error: Exception): ResponseEntity<ApiResponse<Nothing>> =
        failure(HttpStatus.INTERNAL_SERVER_ERROR, ApiErrorCode.INTERNAL_ERROR, "internal server error")

    private fun failure(
        status: HttpStatus,
        code: ApiErrorCode,
        message: String,
    ): ResponseEntity<ApiResponse<Nothing>> = ResponseEntity.status(status).body(ApiResponse.failure(code, message))

    private fun failure(
        status: Int,
        message: String,
    ): ResponseEntity<ApiResponse<Nothing>> = failure(HttpStatus.valueOf(status), status.toErrorCode(), message)

    private fun Int.toErrorCode(): ApiErrorCode =
        when (this) {
            400 -> ApiErrorCode.INVALID_REQUEST
            401 -> ApiErrorCode.UNAUTHORIZED
            403 -> ApiErrorCode.FORBIDDEN
            404 -> ApiErrorCode.NOT_FOUND
            409 -> ApiErrorCode.CONFLICT
            else -> ApiErrorCode.INTERNAL_ERROR
        }
}
