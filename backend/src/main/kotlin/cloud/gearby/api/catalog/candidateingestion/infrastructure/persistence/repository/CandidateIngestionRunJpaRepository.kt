package cloud.gearby.api.catalog.candidateingestion.infrastructure.persistence.repository

import cloud.gearby.api.catalog.candidateingestion.domain.IngestionRunStatus
import cloud.gearby.api.catalog.candidateingestion.infrastructure.persistence.entity.CandidateIngestionRunEntity
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface CandidateIngestionRunJpaRepository : JpaRepository<CandidateIngestionRunEntity, UUID> {
    fun findByProviderKeyAndIdempotencyKey(
        providerKey: String,
        idempotencyKey: String,
    ): CandidateIngestionRunEntity?

    @Query(
        """
        SELECT r FROM CandidateIngestionRunEntity r
        WHERE (:status IS NULL OR r.status = :status)
          AND (:provider IS NULL OR r.providerKey = :provider)
        ORDER BY r.requestedAt DESC, r.id ASC
        """,
    )
    fun findAdminPage(
        status: IngestionRunStatus?,
        provider: String?,
        pageable: Pageable,
    ): Page<CandidateIngestionRunEntity>
}
