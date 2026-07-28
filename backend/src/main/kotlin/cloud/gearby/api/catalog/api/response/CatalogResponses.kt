package cloud.gearby.api.catalog.api.response

import cloud.gearby.api.catalog.domain.StoreInformationStatus
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class CategoryResponse(
    val slug: String,
    val displayName: String,
)

data class CoordinatesResponse(
    val latitude: BigDecimal,
    val longitude: BigDecimal,
)

data class SearchDisclosureResponse(
    val originalQuery: String,
    val appliedQuery: String,
    val correction: String?,
)

data class StoreResponse(
    val id: UUID,
    val name: String,
    val address: String,
    val coordinates: CoordinatesResponse,
    val categories: List<String>,
    val phone: String?,
    val hours: String?,
    val description: String?,
    val verifiedAt: Instant,
    val informationStatus: StoreInformationStatus,
)

data class StorePageResponse(
    val items: List<StoreResponse>,
    val nextCursor: String?,
    val search: SearchDisclosureResponse? = null,
)
