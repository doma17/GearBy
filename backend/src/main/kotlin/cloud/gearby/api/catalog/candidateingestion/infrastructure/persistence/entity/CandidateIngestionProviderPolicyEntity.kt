package cloud.gearby.api.catalog.candidateingestion.infrastructure.persistence.entity

import cloud.gearby.api.catalog.candidateingestion.domain.ProviderApprovalStatus
import cloud.gearby.api.catalog.infrastructure.persistence.entity.AuditableEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "candidate_ingestion_provider_policy")
class CandidateIngestionProviderPolicyEntity(
    @Id var id: UUID = UUID.randomUUID(),
    @Column(name = "provider_key") var providerKey: String = "",
    @Enumerated(EnumType.STRING)
    @Column(name = "approval_status")
    var approvalStatus: ProviderApprovalStatus = ProviderApprovalStatus.PENDING,
    @Column(name = "approval_owner") var approvalOwner: String? = null,
    @Column(name = "reviewed_at") var reviewedAt: Instant? = null,
    @Column(name = "approved_source_url") var approvedSourceUrl: String? = null,
    @Column(name = "allowed_fields") var allowedFields: String = "",
    @Column(name = "retention_rules") var retentionRules: String = "digest-only",
    @Column(name = "gate_version") var gateVersion: String = "",
    @Column(name = "sample_precision_result_reference") var samplePrecisionResultReference: String? = null,
    @Column(name = "sample_size") var sampleSize: Int = 0,
    @Column(name = "region_count") var regionCount: Int = 0,
    @Column(name = "precision_threshold") var precisionThreshold: BigDecimal = BigDecimal.ZERO,
    var active: Boolean = false,
    var notes: String? = null,
) : AuditableEntity()
