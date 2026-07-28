package cloud.gearby.api.catalog.api

import cloud.gearby.api.catalog.api.response.CoordinatesResponse
import cloud.gearby.api.catalog.api.response.FeedbackReceiptResponse
import cloud.gearby.api.catalog.api.response.SearchDisclosureResponse
import cloud.gearby.api.catalog.api.response.StorePageResponse
import cloud.gearby.api.catalog.api.response.StoreResponse
import cloud.gearby.api.catalog.application.result.FeedbackReceiptResult
import cloud.gearby.api.catalog.application.result.StorePageResult
import cloud.gearby.api.catalog.application.result.StoreResult

fun StoreResult.toResponse() =
    StoreResponse(
        id,
        name,
        address,
        CoordinatesResponse(coordinates.latitude, coordinates.longitude),
        categories
            .map {
                it.name
            }.sorted(),
        phone,
        hours,
        description,
    )

fun StorePageResult.toResponse() =
    StorePageResponse(
        items.map {
            it.toResponse()
        },
        nextCursor,
        search?.let { SearchDisclosureResponse(it.originalQuery, it.appliedQuery, it.correction) },
    )

fun FeedbackReceiptResult.toResponse() = FeedbackReceiptResponse(id, status)
