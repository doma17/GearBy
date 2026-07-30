package cloud.gearby.api.catalog.candidateingestion.infrastructure.semas

import cloud.gearby.api.catalog.candidateingestion.application.port.ProviderCandidatePage
import cloud.gearby.api.catalog.candidateingestion.application.port.ProviderFailure
import cloud.gearby.api.catalog.candidateingestion.application.port.ProviderFailureCategory
import cloud.gearby.api.catalog.candidateingestion.application.port.StoreCandidateProvider
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException

@Component
@ConditionalOnProperty(prefix = "gearby.ingestion", name = ["provider"], havingValue = "semas")
class SemasStoreCandidateProvider(
    properties: SemasIngestionProperties,
    restClientBuilder: RestClient.Builder,
) : StoreCandidateProvider {
    private val serviceKey = properties.serviceKey.trim()
    private val pageSizeLimit = 1000
    private val baseUrl = properties.baseUrl.trimEnd('/')
    private val restClient = restClientBuilder.baseUrl(baseUrl).build()

    override fun fetchPage(
        industryCode: String,
        pageNo: Int,
        pageSize: Int,
    ): ProviderCandidatePage {
        require(serviceKey.isNotBlank()) { "SEMAS service key is required" }
        val boundedPageSize = pageSize.coerceIn(1, pageSizeLimit)
        val sourceUrl = "$baseUrl$PATH"
        val body =
            try {
                restClient
                    .get()
                    .uri { builder ->
                        builder
                            .path(PATH)
                            .queryParam("ServiceKey", serviceKey)
                            .queryParam("divId", "indsSclsCd")
                            .queryParam("key", industryCode)
                            .queryParam("pageNo", pageNo)
                            .queryParam("numOfRows", boundedPageSize)
                            .queryParam("type", "json")
                            .build()
                    }.retrieve()
                    .body(String::class.java)
            } catch (ex: RestClientResponseException) {
                throw ProviderFailure(ex.statusCategory(), "SEMAS provider request failed", ex)
            } catch (ex: Exception) {
                throw ProviderFailure(ProviderFailureCategory.NETWORK, "SEMAS provider request failed", ex)
            } ?: throw ProviderFailure(ProviderFailureCategory.MALFORMED_RESPONSE, "SEMAS response body is empty")
        val page = SemasStoreCandidateParser.parsePage(body, sourceUrl)
        val hasNext =
            if (page.totalCount != null && page.pageNo != null && page.numOfRows != null) {
                page.pageNo * page.numOfRows < page.totalCount
            } else {
                page.candidates.size >= boundedPageSize
            }
        return ProviderCandidatePage(candidates = page.candidates, hasNext = hasNext)
    }

    private fun RestClientResponseException.statusCategory() =
        when (statusCode.value()) {
            401, 403 -> ProviderFailureCategory.AUTH
            429 -> ProviderFailureCategory.QUOTA
            else -> ProviderFailureCategory.NETWORK
        }

    companion object {
        const val PATH = "/B553077/api/open/sdsc2/storeListInUpjong"
    }
}
