package cloud.gearby.api.catalog.candidateingestion.application.port

class ProviderFailure(
    val category: ProviderFailureCategory,
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
