package cloud.gearby.api.catalog.candidateingestion.application.port

import cloud.gearby.api.catalog.candidateingestion.domain.ExternalStoreCandidate

data class ProviderCandidatePage(
    val candidates: List<ExternalStoreCandidate>,
    val hasNext: Boolean,
)
