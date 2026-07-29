package cloud.gearby.api.catalog.candidateingestion.infrastructure.persistence.entity

import cloud.gearby.api.catalog.candidateingestion.domain.CandidateItemOutcome
import cloud.gearby.api.catalog.candidateingestion.domain.CandidateMatchPrecedence
import cloud.gearby.api.catalog.candidateingestion.domain.CandidateMatchStatus
import cloud.gearby.api.catalog.candidateingestion.domain.CandidateSourceType
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
@Table(name = "store_candidate_provenance")
class StoreCandidateProvenanceEntity(
    @Id var id: UUID = UUID.randomUUID(),
    @Column(name = "run_id") var runId: UUID,
    @Column(name = "provider_key") var providerKey: String,
    @Column(name = "provider_record_id") var providerRecordId: String? = null,
    @Column(name = "dedup_key") var dedupKey: String,
    @Column(name = "first_seen_run_id") var firstSeenRunId: UUID,
    @Column(name = "last_seen_run_id") var lastSeenRunId: UUID,
    @Column(name = "first_seen_at") var firstSeenAt: Instant,
    @Column(name = "last_seen_at") var lastSeenAt: Instant,
    @Enumerated(EnumType.STRING) @Column(name = "source_type") var sourceType: CandidateSourceType = CandidateSourceType.API,
    @Column(name = "source_url") var sourceUrl: String,
    @Column(name = "normalized_name") var normalizedName: String,
    @Column(name = "road_address") var roadAddress: String? = null,
    @Column(name = "rounded_latitude") var roundedLatitude: BigDecimal? = null,
    @Column(name = "rounded_longitude") var roundedLongitude: BigDecimal? = null,
    var phone: String? = null,
    @Column(name = "industry_code") var industryCode: String? = null,
    @Enumerated(EnumType.STRING) @Column(name = "match_precedence") var matchPrecedence: CandidateMatchPrecedence,
    @Enumerated(EnumType.STRING) @Column(name = "match_status") var matchStatus: CandidateMatchStatus,
    @Column(name = "match_reason") var matchReason: String? = null,
    @Enumerated(EnumType.STRING) @Column(name = "latest_item_outcome") var latestItemOutcome: CandidateItemOutcome,
    @Column(name = "resolved_store_id") var resolvedStoreId: UUID? = null,
    @Column(name = "payload_sha256_digest") var payloadSha256Digest: String,
) : AuditableEntity()
