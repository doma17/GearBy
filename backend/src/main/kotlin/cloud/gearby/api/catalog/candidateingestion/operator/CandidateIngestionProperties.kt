package cloud.gearby.api.catalog.candidateingestion.operator

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("gearby.ingestion")
data class CandidateIngestionProperties(
    val enabled: Boolean = false,
    val provider: String = "",
    val runKey: String = "",
    val requestedBy: String = "candidate-ingestion-runner",
    val allowlistVersion: String = "",
    val industryCodes: List<String> = emptyList(),
    val pageSize: Int = 100,
    val maxPages: Int = 10,
) {
    fun validate(semasServiceKey: String) {
        require(provider == "semas") { "gearby.ingestion.provider must be semas" }
        require(runKey.isNotBlank()) { "gearby.ingestion.run-key is required" }
        require(allowlistVersion.isNotBlank()) { "gearby.ingestion.allowlist-version is required" }
        require(industryCodes.isNotEmpty()) { "gearby.ingestion.industry-codes must not be empty" }
        require(industryCodes.all { it.matches(Regex("^\\d{6}$")) }) { "industry codes must be 6 digits" }
        require(pageSize in 1..1000) { "gearby.ingestion.page-size must be between 1 and 1000" }
        require(maxPages in 1..1000) { "gearby.ingestion.max-pages must be between 1 and 1000" }
        require(semasServiceKey.isNotBlank()) { "SEMAS_SERVICE_KEY is required" }
    }
}
