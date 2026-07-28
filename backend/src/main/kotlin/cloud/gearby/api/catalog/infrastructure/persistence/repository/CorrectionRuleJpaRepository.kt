package cloud.gearby.api.catalog.infrastructure.persistence.repository

import cloud.gearby.api.catalog.infrastructure.persistence.entity.CorrectionRuleEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface CorrectionRuleJpaRepository : JpaRepository<CorrectionRuleEntity, UUID> {
    fun findByOrderBySourceAsc(): List<CorrectionRuleEntity>

    fun findBySourceAndActiveTrue(source: String): CorrectionRuleEntity?
}
