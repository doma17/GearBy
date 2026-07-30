package cloud.gearby.api.catalog.candidateingestion.domain

enum class CandidateItemOutcome {
    DRAFT_CREATED,
    MATCHED_EXISTING,
    DUPLICATE_SKIPPED,
    QUARANTINED,
    BLOCKED_BY_GATE,
    REJECTED,
    ITEM_FAILED,
    RESOLVED,
}
