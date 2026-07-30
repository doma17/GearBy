package cloud.gearby.api.catalog.candidateingestion.domain

enum class CandidateMatchStatus {
    NOT_EVALUATED,
    NO_MATCH,
    EXACT_PROVIDER_RECORD,
    EXACT_NAME_ADDRESS,
    EXACT_NAME_COORDINATES,
    AMBIGUOUS,
    RESOLVED_EXISTING,
    RESOLVED_DRAFT,
}
