package cloud.gearby.api.catalog.infrastructure.implement

import cloud.gearby.api.catalog.domain.Category
import cloud.gearby.api.catalog.domain.CategoryReviewFlagState
import cloud.gearby.api.catalog.domain.StoreStatus
import cloud.gearby.api.catalog.infrastructure.persistence.entity.AuditEventEntity
import cloud.gearby.api.catalog.infrastructure.persistence.entity.CategoryReviewFlagEntity
import cloud.gearby.api.catalog.infrastructure.persistence.entity.CorrectionRuleEntity
import cloud.gearby.api.catalog.infrastructure.persistence.entity.FeedbackEntity
import cloud.gearby.api.catalog.infrastructure.persistence.entity.StoreEntity
import cloud.gearby.api.catalog.infrastructure.persistence.repository.AuditEventJpaRepository
import cloud.gearby.api.catalog.infrastructure.persistence.repository.CategoryReviewFlagJpaRepository
import cloud.gearby.api.catalog.infrastructure.persistence.repository.CorrectionRuleJpaRepository
import cloud.gearby.api.catalog.infrastructure.persistence.repository.FeedbackJpaRepository
import cloud.gearby.api.catalog.infrastructure.persistence.repository.FeedbackNotificationAttemptJpaRepository
import cloud.gearby.api.catalog.infrastructure.persistence.repository.StoreJpaRepository
import java.util.UUID
import org.springframework.stereotype.Component

@Component
class CatalogReader(
    private val stores: StoreJpaRepository,
    private val rules: CorrectionRuleJpaRepository,
    private val feedback: FeedbackJpaRepository,
    private val flags: CategoryReviewFlagJpaRepository,
    private val attempts: FeedbackNotificationAttemptJpaRepository,
    private val audits: AuditEventJpaRepository,
) {
    fun categories(): List<Category> = Category.entries
    fun findStore(id: UUID): StoreEntity? = stores.findById(id).orElse(null)
    fun storesByStatus(status: StoreStatus): List<StoreEntity> = stores.findByStatusOrderByNameAscIdAsc(status)
    fun allStores(): List<StoreEntity> = stores.findAll()
    fun correctionRules(): List<CorrectionRuleEntity> = rules.findByOrderBySourceAsc()
    fun correctionFor(source: String): CorrectionRuleEntity? = rules.findBySourceAndActiveTrue(source)
    fun feedback(): List<FeedbackEntity> = feedback.findByOrderBySubmittedAtDescIdAsc()
    fun feedback(id: UUID): FeedbackEntity? = feedback.findById(id).orElse(null)
    fun categoryReviewFlags(state: CategoryReviewFlagState? = null, storeId: UUID? = null, assignee: String? = null): List<CategoryReviewFlagEntity> = flags.findAll()
        .filter { state == null || it.state == state }
        .filter { storeId == null || it.storeId == storeId }
        .filter { assignee == null || it.assignee == assignee.trim().ifBlank { null } }
        .sortedWith(compareByDescending<CategoryReviewFlagEntity> { it.createdAt }.thenBy { it.id })
    fun notificationAttempts(id: UUID): Int = attempts.countByFeedbackId(id)
    fun auditEvents(id: UUID): List<AuditEventEntity> = audits.findByResourceIdOrderByCreatedAtAsc(id)
}
