package cloud.gearby.api.catalog.candidateingestion.infrastructure.semas

import cloud.gearby.api.catalog.candidateingestion.domain.ExternalStoreCandidate

data class SemasParsedPage(
    val candidates: List<ExternalStoreCandidate>,
    val totalCount: Int?,
    val pageNo: Int?,
    val numOfRows: Int?,
)
