package cloud.gearby.api.catalog.api

import cloud.gearby.api.catalog.api.request.FeedbackRequest
import cloud.gearby.api.catalog.api.response.CategoryResponse
import cloud.gearby.api.catalog.api.response.FeedbackReceiptResponse
import cloud.gearby.api.catalog.api.response.StorePageResponse
import cloud.gearby.api.catalog.api.response.StoreResponse
import cloud.gearby.api.catalog.application.command.FeedbackSubmitCommand
import cloud.gearby.api.catalog.application.query.StoreQuery
import cloud.gearby.api.catalog.application.service.CatalogService
import cloud.gearby.api.catalog.domain.Bbox
import cloud.gearby.api.catalog.domain.Category
import cloud.gearby.api.catalog.domain.Coordinates
import cloud.gearby.api.catalog.domain.StoreStatus
import cloud.gearby.api.response.ApiResponse
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.math.BigDecimal
import java.util.UUID

/** Exposes the public catalog HTTP contract. */
@RestController
@RequestMapping("/api/v1")
class CatalogController(
    private val catalog: CatalogService,
) {
    @GetMapping("/categories")
    fun categories(): ApiResponse<List<CategoryResponse>> =
        ApiResponse.success(
            catalog.categories().map {
                CategoryResponse(it.name, it.displayName)
            },
        )

    @GetMapping("/stores")
    fun stores(
        @RequestParam(required = false) category: Set<Category>?,
        @RequestParam(required = false, name = "q") query: String?,
        @RequestParam(defaultValue = "true") applyCorrection: Boolean,
        @RequestParam(required = false) bbox: String?,
        @RequestParam(required = false) near: String?,
        @RequestParam(defaultValue = "name") sort: String,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(defaultValue = "20") limit: Int,
    ): ApiResponse<StorePageResponse> =
        ApiResponse.success(
            catalog
                .search(StoreQuery(category.orEmpty(), query, applyCorrection, bbox?.toBbox(), near?.toCoordinates(), sort, cursor, limit))
                .toResponse(),
        )

    @GetMapping("/stores/{storeId}")
    fun store(
        @PathVariable storeId: UUID,
    ): ApiResponse<StoreResponse> =
        ApiResponse.success(
            catalog.find(storeId)?.takeIf { it.status == StoreStatus.PUBLISHED }?.toResponse()
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "store not found"),
        )

    @PostMapping("/feedback")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun feedback(
        @RequestBody request: FeedbackRequest,
    ): ApiResponse<FeedbackReceiptResponse> = ApiResponse.success(catalog.submitFeedback(request.toCommand()).toResponse())
}

private fun FeedbackRequest.toCommand() = FeedbackSubmitCommand(kind, storeId, content, replyEmail, contactConsent, categoryRelated)

private fun String.toCoordinates(): Coordinates {
    val (latitude, longitude) =
        split(',')
            .map {
                it.trim().toBigDecimalOrNull() ?: throw IllegalArgumentException("invalid near")
            }.takeExactly(2)
    require(latitude in BigDecimal(-90)..BigDecimal(90) && longitude in BigDecimal(-180)..BigDecimal(180)) { "invalid near" }
    return Coordinates(latitude, longitude)
}

private fun String.toBbox(): Bbox {
    val (minLongitude, minLatitude, maxLongitude, maxLatitude) =
        split(',')
            .map {
                it.trim().toBigDecimalOrNull()
                    ?: throw IllegalArgumentException("invalid bbox")
            }.takeExactly(4)
    require(
        minLongitude in BigDecimal(-180)..BigDecimal(180) &&
            maxLongitude in BigDecimal(-180)..BigDecimal(180) &&
            minLatitude in BigDecimal(-90)..BigDecimal(90) &&
            maxLatitude in BigDecimal(-90)..BigDecimal(90),
    ) { "invalid bbox" }
    return Bbox(minLongitude, minLatitude, maxLongitude, maxLatitude)
}

private fun <T> List<T>.takeExactly(size: Int): List<T> =
    takeIf { it.size == size } ?: throw IllegalArgumentException("invalid coordinate parameter")
