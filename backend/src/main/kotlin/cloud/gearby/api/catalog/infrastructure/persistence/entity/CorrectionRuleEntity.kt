package cloud.gearby.api.catalog.infrastructure.persistence.entity

import cloud.gearby.api.catalog.domain.CorrectionTargetType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "correction_rules")
class CorrectionRuleEntity(
    @Id var id: UUID = UUID.randomUUID(),
    @Column(name = "normalized_source") var source: String = "",
    @Enumerated(EnumType.STRING) @Column(name = "target_type") var targetType: CorrectionTargetType = CorrectionTargetType.STORE,
    var target: String = "",
    var active: Boolean = true,
) : AuditableEntity()
