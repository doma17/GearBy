package cloud.gearby.api.catalog.candidateingestion.api.request

import java.math.BigDecimal

data class CandidateResolutionCoordinatesRequest(
    val latitude: BigDecimal,
    val longitude: BigDecimal,
)
