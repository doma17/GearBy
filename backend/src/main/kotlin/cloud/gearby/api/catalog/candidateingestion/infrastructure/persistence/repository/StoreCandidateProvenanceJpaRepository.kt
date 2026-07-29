package cloud.gearby.api.catalog.candidateingestion.infrastructure.persistence.repository

import cloud.gearby.api.catalog.candidateingestion.domain.CandidateItemOutcome
import cloud.gearby.api.catalog.candidateingestion.domain.CandidateMatchStatus
import cloud.gearby.api.catalog.candidateingestion.infrastructure.persistence.entity.StoreCandidateProvenanceEntity
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface StoreCandidateProvenanceJpaRepository : JpaRepository<StoreCandidateProvenanceEntity, UUID> {
    fun findByProviderKeyAndProviderRecordId(
        providerKey: String,
        providerRecordId: String,
    ): StoreCandidateProvenanceEntity?

    fun findByProviderKeyAndDedupKey(
        providerKey: String,
        dedupKey: String,
    ): StoreCandidateProvenanceEntity?

    @Query(
        """
        SELECT p FROM StoreCandidateProvenanceEntity p
        WHERE p.firstSeenRunId = :runId OR p.lastSeenRunId = :runId
        ORDER BY p.createdAt ASC, p.id ASC
        """,
    )
    fun findByRunProvenanceOrderByCreatedAtAscIdAsc(runId: UUID): List<StoreCandidateProvenanceEntity>

    @Query(
        """
        SELECT p FROM StoreCandidateProvenanceEntity p
        WHERE (:runId IS NULL OR p.firstSeenRunId = :runId OR p.lastSeenRunId = :runId)
          AND (:latestOutcome IS NULL OR p.latestItemOutcome = :latestOutcome)
          AND (:latestMatchStatus IS NULL OR p.matchStatus = :latestMatchStatus)
        """,
    )
    fun findAdminPage(
        runId: UUID?,
        latestOutcome: CandidateItemOutcome?,
        latestMatchStatus: CandidateMatchStatus?,
        pageable: Pageable,
    ): Page<StoreCandidateProvenanceEntity>
}
