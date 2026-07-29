package cloud.gearby.api.catalog.candidateingestion.application.result

import cloud.gearby.api.catalog.candidateingestion.domain.IngestionRunStatus
import java.util.UUID

data class CandidateIngestionResult(
    val runId: UUID,
    val status: IngestionRunStatus,
    val seenCount: Int,
    val acceptedCount: Int,
    val dedupedCount: Int,
    val quarantinedCount: Int,
    val rejectedCount: Int,
    val failedCount: Int,
    val idempotent: Boolean,
)
