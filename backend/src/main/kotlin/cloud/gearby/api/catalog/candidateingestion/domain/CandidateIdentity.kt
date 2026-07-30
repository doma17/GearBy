package cloud.gearby.api.catalog.candidateingestion.domain

data class CandidateIdentity(
    val precedence: CandidateMatchPrecedence,
    val dedupKey: String,
)
