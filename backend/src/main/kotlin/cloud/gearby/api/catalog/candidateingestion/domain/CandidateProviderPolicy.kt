package cloud.gearby.api.catalog.candidateingestion.domain

import java.math.BigDecimal
import java.time.Instant

data class CandidateProviderPolicy(
    val providerKey: String,
    val approvalStatus: ProviderApprovalStatus,
    val reviewedAt: Instant? = null,
    val approvedSourceUrl: String? = null,
    val gateVersion: String,
    val sampleSize: Int,
    val regionCount: Int,
    val precisionThreshold: BigDecimal,
    val active: Boolean,
) {
    fun allowsSemasIngestion(): Boolean =
        providerKey == "semas" &&
            active &&
            approvalStatus == ProviderApprovalStatus.APPROVED &&
            reviewedAt != null &&
            !approvedSourceUrl.isNullOrBlank() &&
            sampleSize >= 100 &&
            regionCount >= 5 &&
            precisionThreshold >= BigDecimal("90.00")
}
