package cloud.gearby.api.catalog.application.query

import cloud.gearby.api.catalog.domain.Bbox
import cloud.gearby.api.catalog.domain.Category
import cloud.gearby.api.catalog.domain.Coordinates

data class StoreQuery(
    val categories: Set<Category> = emptySet(),
    val query: String? = null,
    val bbox: Bbox? = null,
    val near: Coordinates? = null,
    val sort: String = "name",
    val cursor: String? = null,
    val limit: Int = 20,
)
