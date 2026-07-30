package cloud.gearby.api.catalog.candidateingestion.application.command

import cloud.gearby.api.catalog.candidateingestion.domain.ExternalStoreCandidate

data class CandidateIngestionCommand(
    val providerKey: String,
    val idempotencyKey: String,
    val requestedBy: String,
    val candidates: List<ExternalStoreCandidate>,
)
