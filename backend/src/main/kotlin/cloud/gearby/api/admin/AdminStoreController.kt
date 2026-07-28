package cloud.gearby.api.admin

import cloud.gearby.api.admin.request.StoreRejectionRequest
import cloud.gearby.api.admin.request.StoreRequest
import cloud.gearby.api.admin.response.AdminStoreResponse
import cloud.gearby.api.catalog.api.response.CoordinatesResponse
import cloud.gearby.api.catalog.application.command.StoreUpsertCommand
import cloud.gearby.api.catalog.application.result.StoreResult
import cloud.gearby.api.catalog.application.service.CatalogService
import cloud.gearby.api.catalog.domain.Coordinates
import cloud.gearby.api.catalog.domain.StoreStatus
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
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

@RestController
@RequestMapping("/api/v1/admin/stores")
class AdminStoreController(private val catalog: CatalogService) {
    @GetMapping
    fun list(): ApiResponse<Map<String, List<AdminStoreResponse>>> =
        ApiResponse.success(StoreStatus.entries.associate { it.name to catalog.findByStatus(it).map(StoreResult::toAdminResponse) })

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@RequestBody request: StoreRequest, principal: Principal): ApiResponse<AdminStoreResponse> =
        ApiResponse.success(catalog.create(request.toCommand(), principal.name).toAdminResponse())

    @PatchMapping("/{storeId}")
    fun update(@PathVariable storeId: UUID, @RequestBody request: StoreRequest, principal: Principal): ApiResponse<AdminStoreResponse> =
        ApiResponse.success(
            catalog.update(storeId, request.toCommand(), principal.name)?.toAdminResponse()
                ?: throw ResponseStatusException(HttpStatus.CONFLICT, "store is not editable"),
        )

    @PostMapping("/{storeId}/review")
    fun review(@PathVariable storeId: UUID, principal: Principal): ApiResponse<AdminStoreResponse> = ApiResponse.success(transition(storeId, StoreStatus.IN_REVIEW, principal))

    @PostMapping("/{storeId}/publish")
    fun publish(@PathVariable storeId: UUID, principal: Principal): ApiResponse<AdminStoreResponse> = ApiResponse.success(transition(storeId, StoreStatus.PUBLISHED, principal))

    @PostMapping("/{storeId}/reject")
    fun reject(@PathVariable storeId: UUID, @RequestBody(required = false) request: StoreRejectionRequest?, principal: Principal): ApiResponse<AdminStoreResponse> =
        ApiResponse.success(transition(storeId, StoreStatus.REJECTED, principal, request?.reason))

    private fun transition(id: UUID, target: StoreStatus, principal: Principal, reason: String? = null): AdminStoreResponse =
        catalog.transition(id, target, principal.name, reason)?.toAdminResponse()
            ?: throw ResponseStatusException(HttpStatus.CONFLICT, "invalid lifecycle transition")
}

private fun StoreRequest.toCommand() = StoreUpsertCommand(name, address, Coordinates(coordinates.latitude, coordinates.longitude), categories, phone, hours, description)
private fun StoreResult.toAdminResponse() = AdminStoreResponse(id, name, address, CoordinatesResponse(coordinates.latitude, coordinates.longitude), categories.map { it.name }.sorted(), phone, hours, description, status!!.name)
