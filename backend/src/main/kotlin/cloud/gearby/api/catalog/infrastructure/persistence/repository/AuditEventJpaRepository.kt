package cloud.gearby.api.catalog.infrastructure.persistence.repository

import cloud.gearby.api.catalog.infrastructure.persistence.entity.AuditEventEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface AuditEventJpaRepository : JpaRepository<AuditEventEntity, UUID> {
    fun findByResourceIdOrderByCreatedAtAsc(resourceId: UUID): List<AuditEventEntity>
}
