package cloud.gearby.api.catalog.candidateingestion.application.port

interface StoreCandidateProvider {
    fun fetchPage(
        industryCode: String,
        pageNo: Int,
        pageSize: Int,
    ): ProviderCandidatePage
}
