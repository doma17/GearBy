package cloud.gearby.api.catalog.candidateingestion.domain

import cloud.gearby.api.catalog.domain.Category
import java.math.BigDecimal

data class NormalizedStoreCandidate(
    val providerRecordId: String?,
    val normalizedName: String,
    val roadAddress: String?,
    val roundedLatitude: BigDecimal?,
    val roundedLongitude: BigDecimal?,
    val phone: String?,
    val industryCode: String?,
    val sourceUrl: String,
    val categories: Set<Category>,
    val payloadSha256Digest: String,
    val identity: CandidateIdentity,
)
