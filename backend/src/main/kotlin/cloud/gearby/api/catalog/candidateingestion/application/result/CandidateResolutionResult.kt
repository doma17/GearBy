package cloud.gearby.api.catalog.candidateingestion.application.result

import cloud.gearby.api.catalog.candidateingestion.domain.CandidateItemOutcome
import cloud.gearby.api.catalog.candidateingestion.domain.CandidateMatchStatus
import cloud.gearby.api.catalog.domain.StoreStatus
import java.util.UUID

data class CandidateResolutionResult(
    val itemId: UUID,
    val outcome: CandidateItemOutcome,
    val matchStatus: CandidateMatchStatus,
    val resolvedStoreId: UUID,
    val resolvedStoreStatus: StoreStatus,
)
