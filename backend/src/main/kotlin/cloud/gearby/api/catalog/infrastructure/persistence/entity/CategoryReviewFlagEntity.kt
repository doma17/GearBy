package cloud.gearby.api.catalog.infrastructure.persistence.entity

import cloud.gearby.api.catalog.domain.CategoryReviewFlagSource
import cloud.gearby.api.catalog.domain.CategoryReviewFlagState
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "category_review_flags")
class CategoryReviewFlagEntity(
    @Id var id: UUID = UUID.randomUUID(),
    @Column(name = "store_id") var storeId: UUID = UUID.randomUUID(),
    @Enumerated(EnumType.STRING) var source: CategoryReviewFlagSource = CategoryReviewFlagSource.MANUAL,
    @Column(name = "source_feedback_id") var sourceFeedbackId: UUID? = null,
    var reason: String = "",
    @Enumerated(EnumType.STRING) var state: CategoryReviewFlagState = CategoryReviewFlagState.OPEN,
    var assignee: String? = null,
    var resolution: String? = null,
    @Column(name = "resolved_at") var resolvedAt: Instant? = null,
    @Column(name = "resolved_by") var resolvedBy: String? = null,
) : AuditableEntity()
