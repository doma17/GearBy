package cloud.gearby.api.catalog.candidateingestion.application.query

import cloud.gearby.api.catalog.candidateingestion.domain.CandidateItemOutcome
import cloud.gearby.api.catalog.candidateingestion.domain.CandidateMatchStatus
import java.util.UUID

data class CandidateItemListQuery(
    val page: Int = 0,
    val size: Int = 20,
    val runId: UUID? = null,
    val latestOutcome: CandidateItemOutcome? = null,
    val latestMatchStatus: CandidateMatchStatus? = null,
)
