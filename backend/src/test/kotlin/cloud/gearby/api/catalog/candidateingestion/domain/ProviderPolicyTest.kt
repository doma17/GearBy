package cloud.gearby.api.catalog.candidateingestion.domain

import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@Tag("unit")
class ProviderPolicyTest {
    @Test
    fun `semas is allowed only with active approved precision gate`() {
        assertTrue(
            CandidateProviderPolicy(
                providerKey = "semas",
                approvalStatus = ProviderApprovalStatus.APPROVED,
                reviewedAt = Instant.parse("2026-01-01T00:00:00Z"),
                approvedSourceUrl = "https://example.test/approval",
                gateVersion = "gate-v1",
                sampleSize = 100,
                regionCount = 5,
                precisionThreshold = BigDecimal("90.00"),
                active = true,
            ).allowsSemasIngestion(),
        )
        assertFalse(
            CandidateProviderPolicy(
                providerKey = "semas",
                approvalStatus = ProviderApprovalStatus.PENDING,
                gateVersion = "gate-v1",
                sampleSize = 100,
                regionCount = 5,
                precisionThreshold = BigDecimal("90.00"),
                active = true,
            ).allowsSemasIngestion(),
        )
        assertFalse(
            CandidateProviderPolicy(
                providerKey = "naver-local-search",
                approvalStatus = ProviderApprovalStatus.APPROVED,
                gateVersion = "gate-v1",
                sampleSize = 100,
                regionCount = 5,
                precisionThreshold = BigDecimal("90.00"),
                active = true,
            ).allowsSemasIngestion(),
        )
    }
}
