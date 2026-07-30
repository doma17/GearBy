package cloud.gearby.api.catalog.candidateingestion.infrastructure.semas

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("gearby.ingestion.semas")
data class SemasIngestionProperties(
    val serviceKey: String = "",
    val baseUrl: String = DEFAULT_BASE_URL,
) {
    companion object {
        const val DEFAULT_BASE_URL = "https://apis.data.go.kr"
    }
}
