package cloud.gearby.api.catalog.candidateingestion.application.query

import cloud.gearby.api.catalog.candidateingestion.domain.IngestionRunStatus

data class CandidateRunListQuery(
    val page: Int = 0,
    val size: Int = 20,
    val status: IngestionRunStatus? = null,
    val provider: String? = null,
)
