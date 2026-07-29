package cloud.gearby.api.catalog.candidateingestion.application.result

import cloud.gearby.api.catalog.candidateingestion.domain.IngestionRunStatus
import java.time.Instant
import java.util.UUID

data class CandidateRunAdminResult(
    val id: UUID,
    val provider: String,
    val idempotencyKey: String,
    val requestedBy: String,
    val requestedAt: Instant,
    val startedAt: Instant?,
    val finishedAt: Instant?,
    val status: IngestionRunStatus,
    val gateVersion: String,
    val seenCount: Int,
    val acceptedCount: Int,
    val dedupedCount: Int,
    val quarantinedCount: Int,
    val rejectedCount: Int,
    val failedCount: Int,
    val errorCode: String?,
    val errorSummary: String?,
)
