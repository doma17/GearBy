package cloud.gearby.api.catalog.candidateingestion.infrastructure.persistence.entity

import cloud.gearby.api.catalog.candidateingestion.domain.IngestionRunStatus
import cloud.gearby.api.catalog.infrastructure.persistence.entity.AuditableEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "candidate_ingestion_runs")
class CandidateIngestionRunEntity(
    @Id var id: UUID = UUID.randomUUID(),
    @Column(name = "provider_policy_id") var providerPolicyId: UUID? = null,
    @Column(name = "provider_key") var providerKey: String = "",
    @Column(name = "idempotency_key") var idempotencyKey: String = "",
    @Column(name = "requested_by") var requestedBy: String = "system",
    @Column(name = "requested_at") var requestedAt: Instant = Instant.now(),
    @Column(name = "started_at") var startedAt: Instant? = null,
    @Column(name = "finished_at") var finishedAt: Instant? = null,
    @Enumerated(EnumType.STRING) var status: IngestionRunStatus = IngestionRunStatus.RUNNING,
    @Column(name = "gate_version") var gateVersion: String = "",
    @Column(name = "seen_count") var seenCount: Int = 0,
    @Column(name = "deduped_count") var dedupedCount: Int = 0,
    @Column(name = "accepted_count") var acceptedCount: Int = 0,
    @Column(name = "quarantined_count") var quarantinedCount: Int = 0,
    @Column(name = "rejected_count") var rejectedCount: Int = 0,
    @Column(name = "failed_count") var failedCount: Int = 0,
    @Column(name = "error_code") var errorCode: String? = null,
    @Column(name = "error_summary") var errorSummary: String? = null,
) : AuditableEntity()
