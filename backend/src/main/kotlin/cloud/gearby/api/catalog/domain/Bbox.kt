package cloud.gearby.api.catalog.domain

import java.math.BigDecimal

data class Bbox(
    val minLongitude: BigDecimal,
    val minLatitude: BigDecimal,
    val maxLongitude: BigDecimal,
    val maxLatitude: BigDecimal,
)
