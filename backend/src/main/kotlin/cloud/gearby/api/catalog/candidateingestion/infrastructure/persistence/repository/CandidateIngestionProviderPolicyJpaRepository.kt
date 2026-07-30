package cloud.gearby.api.catalog.candidateingestion.infrastructure.persistence.repository

import cloud.gearby.api.catalog.candidateingestion.infrastructure.persistence.entity.CandidateIngestionProviderPolicyEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface CandidateIngestionProviderPolicyJpaRepository : JpaRepository<CandidateIngestionProviderPolicyEntity, UUID> {
    fun findFirstByProviderKeyAndActiveTrueOrderByReviewedAtDescIdAsc(providerKey: String): CandidateIngestionProviderPolicyEntity?
}
