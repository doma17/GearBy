package cloud.gearby.api.foundation

import cloud.gearby.api.foundation.response.HealthResponse
import cloud.gearby.api.response.ApiResponse
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1")
class FoundationController {
    @GetMapping("/health", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun health(): ApiResponse<HealthResponse> = ApiResponse.success(HealthResponse(status = "UP"))
}
