package cloud.gearby.api.admin

import cloud.gearby.api.admin.request.CategoryReviewFlagUpdateRequest
import cloud.gearby.api.admin.request.ManualCategoryReviewFlagRequest
import cloud.gearby.api.admin.response.CategoryHealthResponse
import cloud.gearby.api.admin.response.CategoryReviewFlagResponse
import cloud.gearby.api.catalog.application.command.CategoryReviewFlagUpdateCommand
import cloud.gearby.api.catalog.application.command.ManualCategoryReviewFlagCommand
import cloud.gearby.api.catalog.application.service.CatalogService
import cloud.gearby.api.catalog.domain.CategoryReviewFlagState
import cloud.gearby.api.response.ApiResponse
import java.security.Principal
import java.util.UUID
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

@RestController
@RequestMapping("/api/v1/admin")
class AdminCategoryReviewController(private val catalog: CatalogService) {
    @GetMapping("/category-health")
    fun health(): ApiResponse<List<CategoryHealthResponse>> = ApiResponse.success(catalog.categoryHealth().map { it.toResponse() })

    @GetMapping("/category-review-flags")
    fun flags(
        @RequestParam(required = false) state: CategoryReviewFlagState?,
        @RequestParam(required = false) storeId: UUID?,
        @RequestParam(required = false) assignee: String?,
    ): ApiResponse<List<CategoryReviewFlagResponse>> = ApiResponse.success(catalog.categoryReviewFlags(state, storeId, assignee).map { it.toResponse() })

    @PostMapping("/category-review-flags")
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@RequestBody request: ManualCategoryReviewFlagRequest, principal: Principal): ApiResponse<CategoryReviewFlagResponse> =
        ApiResponse.success(catalog.createManualCategoryReviewFlag(request.toCommand(), principal.name).toResponse())

    @PatchMapping("/category-review-flags/{flagId}")
    fun update(@PathVariable flagId: UUID, @RequestBody request: CategoryReviewFlagUpdateRequest, principal: Principal): ApiResponse<CategoryReviewFlagResponse> =
        ApiResponse.success(
            catalog.updateCategoryReviewFlag(flagId, request.toCommand(), principal.name)?.toResponse()
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "category review flag not found"),
        )
}

private fun CategoryReviewFlagUpdateRequest.toCommand() = CategoryReviewFlagUpdateCommand(state, assignee, resolution)
private fun ManualCategoryReviewFlagRequest.toCommand() = ManualCategoryReviewFlagCommand(storeId, reason)
