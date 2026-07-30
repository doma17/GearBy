package cloud.gearby.api.catalog.candidateingestion.infrastructure.implement

import cloud.gearby.api.catalog.candidateingestion.domain.CandidateItemOutcome
import cloud.gearby.api.catalog.candidateingestion.domain.CandidateMatchStatus
import cloud.gearby.api.catalog.candidateingestion.domain.IngestionRunStatus
import cloud.gearby.api.catalog.candidateingestion.infrastructure.persistence.entity.CandidateIngestionProviderPolicyEntity
import cloud.gearby.api.catalog.candidateingestion.infrastructure.persistence.entity.CandidateIngestionRunEntity
import cloud.gearby.api.catalog.candidateingestion.infrastructure.persistence.entity.StoreCandidateProvenanceEntity
import cloud.gearby.api.catalog.candidateingestion.infrastructure.persistence.repository.CandidateIngestionProviderPolicyJpaRepository
import cloud.gearby.api.catalog.candidateingestion.infrastructure.persistence.repository.CandidateIngestionRunJpaRepository
import cloud.gearby.api.catalog.candidateingestion.infrastructure.persistence.repository.StoreCandidateProvenanceJpaRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class CandidateIngestionReader(
    private val policies: CandidateIngestionProviderPolicyJpaRepository,
    private val runs: CandidateIngestionRunJpaRepository,
    private val provenance: StoreCandidateProvenanceJpaRepository,
) {
    fun activePolicy(providerKey: String): CandidateIngestionProviderPolicyEntity? =
        policies.findFirstByProviderKeyAndActiveTrueOrderByReviewedAtDescIdAsc(providerKey)

    fun runByKey(
        providerKey: String,
        idempotencyKey: String,
    ): CandidateIngestionRunEntity? = runs.findByProviderKeyAndIdempotencyKey(providerKey, idempotencyKey)

    fun run(id: UUID): CandidateIngestionRunEntity? = runs.findById(id).orElse(null)

    fun runs(
        status: IngestionRunStatus?,
        provider: String?,
        pageable: Pageable,
    ): Page<CandidateIngestionRunEntity> = runs.findAdminPage(status, provider, pageable)

    fun provenance(id: UUID): StoreCandidateProvenanceEntity? = provenance.findById(id).orElse(null)

    fun provenance(
        runId: UUID?,
        latestOutcome: CandidateItemOutcome?,
        latestMatchStatus: CandidateMatchStatus?,
        pageable: Pageable,
    ): Page<StoreCandidateProvenanceEntity> = provenance.findAdminPage(runId, latestOutcome, latestMatchStatus, pageable)

    fun provenanceByProviderRecord(
        providerKey: String,
        providerRecordId: String,
    ): StoreCandidateProvenanceEntity? = provenance.findByProviderKeyAndProviderRecordId(providerKey, providerRecordId)

    fun provenanceByDedupKey(
        providerKey: String,
        dedupKey: String,
    ): StoreCandidateProvenanceEntity? = provenance.findByProviderKeyAndDedupKey(providerKey, dedupKey)

    fun provenanceForRun(runId: UUID): List<StoreCandidateProvenanceEntity> = provenance.findByRunProvenanceOrderByCreatedAtAscIdAsc(runId)
}
