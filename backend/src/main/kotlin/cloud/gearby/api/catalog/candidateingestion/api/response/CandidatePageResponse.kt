package cloud.gearby.api.catalog.candidateingestion.api.response

data class CandidatePageResponse<T>(
    val items: List<T>,
    val page: Int,
    val size: Int,
    val total: Long,
)
