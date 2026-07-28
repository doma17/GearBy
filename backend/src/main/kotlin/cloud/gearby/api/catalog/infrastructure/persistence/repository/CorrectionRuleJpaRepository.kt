package cloud.gearby.api.catalog.infrastructure.persistence.repository

import cloud.gearby.api.catalog.infrastructure.persistence.entity.CorrectionRuleEntity
import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository

interface CorrectionRuleJpaRepository : JpaRepository<CorrectionRuleEntity, UUID> {
    fun findByOrderBySourceAsc(): List<CorrectionRuleEntity>
    fun findBySourceAndActiveTrue(source: String): CorrectionRuleEntity?
}
