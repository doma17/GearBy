package cloud.gearby.api.catalog.candidateingestion.api.response

import cloud.gearby.api.catalog.candidateingestion.domain.CandidateItemOutcome
import cloud.gearby.api.catalog.candidateingestion.domain.CandidateMatchStatus
import cloud.gearby.api.catalog.domain.StoreStatus
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class CandidateItemResponse(
    val id: UUID,
    val firstSeenRunId: UUID,
    val lastSeenRunId: UUID,
    val provider: String,
    val providerRecordId: String?,
    val sourceUrl: String,
    val normalizedName: String,
    val roadAddress: String?,
    val roundedLatitude: BigDecimal?,
    val roundedLongitude: BigDecimal?,
    val phone: String?,
    val industryCode: String?,
    val latestOutcome: CandidateItemOutcome,
    val latestMatchStatus: CandidateMatchStatus,
    val resolvedStoreId: UUID?,
    val resolvedStoreStatus: StoreStatus?,
    val createdAt: Instant,
    val updatedAt: Instant,
)
