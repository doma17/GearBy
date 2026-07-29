package cloud.gearby.api.catalog.candidateingestion.application.result

import cloud.gearby.api.catalog.candidateingestion.domain.CandidateItemOutcome
import cloud.gearby.api.catalog.candidateingestion.domain.CandidateMatchStatus
import java.util.UUID

data class CandidateItemResult(
    val id: UUID,
    val outcome: CandidateItemOutcome,
    val matchStatus: CandidateMatchStatus,
    val resolvedStoreId: UUID?,
)
