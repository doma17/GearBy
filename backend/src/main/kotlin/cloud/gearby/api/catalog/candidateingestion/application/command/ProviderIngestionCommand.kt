package cloud.gearby.api.catalog.candidateingestion.application.command

data class ProviderIngestionCommand(
    val providerKey: String,
    val idempotencyKey: String,
    val requestedBy: String,
    val allowlistVersion: String,
    val industryCodes: List<String>,
    val pageSize: Int,
    val maxPages: Int,
)
