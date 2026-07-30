package cloud.gearby.api.catalog.candidateingestion.infrastructure.implement

import cloud.gearby.api.catalog.candidateingestion.infrastructure.persistence.entity.CandidateIngestionProviderPolicyEntity
import cloud.gearby.api.catalog.candidateingestion.infrastructure.persistence.entity.CandidateIngestionRunEntity
import cloud.gearby.api.catalog.candidateingestion.infrastructure.persistence.entity.StoreCandidateProvenanceEntity
import cloud.gearby.api.catalog.candidateingestion.infrastructure.persistence.repository.CandidateIngestionProviderPolicyJpaRepository
import cloud.gearby.api.catalog.candidateingestion.infrastructure.persistence.repository.CandidateIngestionRunJpaRepository
import cloud.gearby.api.catalog.candidateingestion.infrastructure.persistence.repository.StoreCandidateProvenanceJpaRepository
import org.springframework.stereotype.Component

@Component
class CandidateIngestionAppender(
    private val policies: CandidateIngestionProviderPolicyJpaRepository,
    private val runs: CandidateIngestionRunJpaRepository,
    private val provenance: StoreCandidateProvenanceJpaRepository,
) {
    fun savePolicy(policy: CandidateIngestionProviderPolicyEntity) = policies.save(policy)

    fun saveRun(run: CandidateIngestionRunEntity) = runs.save(run)

    fun createRun(run: CandidateIngestionRunEntity) = runs.saveAndFlush(run)

    fun saveProvenance(item: StoreCandidateProvenanceEntity) = provenance.save(item)
}
