package cloud.gearby.api.catalog.infrastructure.persistence.repository

import cloud.gearby.api.catalog.infrastructure.persistence.entity.AuditEventEntity
import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository

interface AuditEventJpaRepository : JpaRepository<AuditEventEntity, UUID> {
    fun findByResourceIdOrderByCreatedAtAsc(resourceId: UUID): List<AuditEventEntity>
}
