package cloud.gearby.api.catalog.candidateingestion.api.request

import cloud.gearby.api.catalog.domain.Category
import java.util.UUID

data class CandidateResolutionRequest(
    val resolutionType: CandidateResolutionType,
    val storeId: UUID? = null,
    val name: String? = null,
    val address: String? = null,
    val coordinates: CandidateResolutionCoordinatesRequest? = null,
    val categories: Set<Category>? = null,
    val phone: String? = null,
    val hours: String? = null,
    val description: String? = null,
)
