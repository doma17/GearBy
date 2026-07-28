package cloud.gearby.api.catalog.infrastructure.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.UUID
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

@Entity
@Table(name = "audit_events")
class AuditEventEntity(
    @Id var id: UUID = UUID.randomUUID(),
    var actor: String = "system",
    var action: String = "",
    @Column(name = "resource_type") var resourceType: String = "STORE",
    @Column(name = "resource_id") var resourceId: UUID = UUID.randomUUID(),
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "before_state") var beforeState: String? = null,
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "after_state") var afterState: String? = null,
) : AuditableEntity()
