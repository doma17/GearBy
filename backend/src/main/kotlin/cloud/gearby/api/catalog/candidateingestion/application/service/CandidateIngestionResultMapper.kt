package cloud.gearby.api.catalog.candidateingestion.application.service

import cloud.gearby.api.catalog.candidateingestion.application.result.CandidateIngestionResult
import cloud.gearby.api.catalog.candidateingestion.infrastructure.persistence.entity.CandidateIngestionRunEntity

fun CandidateIngestionRunEntity.toResult(idempotent: Boolean) =
    CandidateIngestionResult(
        runId = id,
        status = status,
        seenCount = seenCount,
        acceptedCount = acceptedCount,
        dedupedCount = dedupedCount,
        quarantinedCount = quarantinedCount,
        rejectedCount = rejectedCount,
        failedCount = failedCount,
        idempotent = idempotent,
    )
