package cloud.gearby.api.catalog.candidateingestion.application.result

data class PageResult<T>(
    val items: List<T>,
    val page: Int,
    val size: Int,
    val total: Long,
)
