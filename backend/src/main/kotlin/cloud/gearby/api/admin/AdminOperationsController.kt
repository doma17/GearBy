package cloud.gearby.api.admin

import cloud.gearby.api.admin.request.CorrectionRuleRequest
import cloud.gearby.api.admin.response.AdminDashboardResponse
import cloud.gearby.api.admin.response.CorrectionRuleResponse
import cloud.gearby.api.catalog.application.command.CorrectionRuleCommand
import cloud.gearby.api.catalog.application.service.CatalogService
import cloud.gearby.api.response.ApiResponse
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.security.Principal
import java.util.UUID

@RestController
@RequestMapping("/api/v1/admin")
class AdminOperationsController(
    private val catalog: CatalogService,
) {
    @GetMapping("/correction-rules")
    fun correctionRules(): ApiResponse<List<CorrectionRuleResponse>> =
        ApiResponse.success(catalog.correctionRules().map { it.toResponse() })

    @PostMapping("/correction-rules")
    @ResponseStatus(HttpStatus.CREATED)
    fun createCorrectionRule(
        @RequestBody request: CorrectionRuleRequest,
        principal: Principal,
    ): ApiResponse<CorrectionRuleResponse> =
        ApiResponse.success(catalog.createCorrectionRule(request.toCommand(), principal.name).toResponse())

    @PatchMapping("/correction-rules/{ruleId}")
    fun updateCorrectionRule(
        @PathVariable ruleId: UUID,
        @RequestBody request: CorrectionRuleRequest,
        principal: Principal,
    ): ApiResponse<CorrectionRuleResponse> =
        ApiResponse.success(
            catalog.updateCorrectionRule(ruleId, request.toCommand(), principal.name)?.toResponse()
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "correction rule not found"),
        )

    @DeleteMapping("/correction-rules/{ruleId}")
    fun deleteCorrectionRule(
        @PathVariable ruleId: UUID,
        principal: Principal,
    ): ApiResponse<Nothing> {
        if (!catalog.deleteCorrectionRule(
                ruleId,
                principal.name,
            )
        ) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "correction rule not found")
        }
        return ApiResponse.success()
    }

    @GetMapping("/dashboard")
    fun dashboard(): ApiResponse<AdminDashboardResponse> = ApiResponse.success(catalog.dashboard().toResponse())
}

private fun CorrectionRuleRequest.toCommand() = CorrectionRuleCommand(source, targetType, target, active)
