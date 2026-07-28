package cloud.gearby.api.admin

import cloud.gearby.api.admin.request.FeedbackResolutionRequest
import cloud.gearby.api.admin.response.AdminFeedbackResponse
import cloud.gearby.api.catalog.application.command.FeedbackResolveCommand
import cloud.gearby.api.catalog.application.service.CatalogService
import cloud.gearby.api.response.ApiResponse
import java.security.Principal
import java.util.UUID
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

@RestController
@RequestMapping("/api/v1/admin/feedback")
class AdminFeedbackController(private val catalog: CatalogService) {
    @GetMapping
    fun list(): ApiResponse<List<AdminFeedbackResponse>> = ApiResponse.success(catalog.feedback().map { it.toResponse() })

    @PatchMapping("/{feedbackId}")
    fun resolve(@PathVariable feedbackId: UUID, @RequestBody request: FeedbackResolutionRequest, principal: Principal): ApiResponse<AdminFeedbackResponse> =
        ApiResponse.success(
            catalog.resolveFeedback(feedbackId, request.toCommand(), principal.name)?.toResponse()
                ?: throw ResponseStatusException(HttpStatus.CONFLICT, "feedback is already resolved or unavailable"),
        )
}

private fun FeedbackResolutionRequest.toCommand() = FeedbackResolveCommand(resolutionStatus, resolutionSummary)
