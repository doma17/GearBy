package cloud.gearby.api.catalog.infrastructure.implement

import cloud.gearby.api.catalog.infrastructure.persistence.entity.AuditEventEntity
import cloud.gearby.api.catalog.infrastructure.persistence.entity.CategoryReviewFlagEntity
import cloud.gearby.api.catalog.infrastructure.persistence.entity.CorrectionRuleEntity
import cloud.gearby.api.catalog.infrastructure.persistence.entity.FeedbackEntity
import cloud.gearby.api.catalog.infrastructure.persistence.entity.FeedbackNotificationAttemptEntity
import cloud.gearby.api.catalog.infrastructure.persistence.entity.StoreEntity
import cloud.gearby.api.catalog.infrastructure.persistence.repository.AuditEventJpaRepository
import cloud.gearby.api.catalog.infrastructure.persistence.repository.CategoryReviewFlagJpaRepository
import cloud.gearby.api.catalog.infrastructure.persistence.repository.CorrectionRuleJpaRepository
import cloud.gearby.api.catalog.infrastructure.persistence.repository.FeedbackJpaRepository
import cloud.gearby.api.catalog.infrastructure.persistence.repository.FeedbackNotificationAttemptJpaRepository
import cloud.gearby.api.catalog.infrastructure.persistence.repository.StoreJpaRepository
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class CatalogAppender(
    private val stores: StoreJpaRepository,
    private val rules: CorrectionRuleJpaRepository,
    private val feedback: FeedbackJpaRepository,
    private val flags: CategoryReviewFlagJpaRepository,
    private val attempts: FeedbackNotificationAttemptJpaRepository,
    private val audits: AuditEventJpaRepository,
) {
    fun saveStore(store: StoreEntity) = stores.save(store)

    fun saveRule(rule: CorrectionRuleEntity) = rules.save(rule)

    fun deleteRule(rule: CorrectionRuleEntity) = rules.delete(rule)

    fun saveFeedback(item: FeedbackEntity) = feedback.save(item)

    fun saveFlag(flag: CategoryReviewFlagEntity) = flags.save(flag)

    fun appendNotificationAttempt(
        feedbackId: UUID,
        recipientEmail: String,
        actor: String,
    ) {
        // The unique attempt check keeps feedback resolution retry-safe.
        if (!attempts.existsByFeedbackId(feedbackId)) {
            attempts.save(
                FeedbackNotificationAttemptEntity(feedbackId = feedbackId, recipientEmail = recipientEmail).apply { createdBy(actor) },
            )
        }
    }

    fun audit(
        action: String,
        id: UUID,
        actor: String,
        before: String?,
        after: String?,
        resourceType: String = "STORE",
    ) {
        // Persist scalar snapshots as JSON strings so audit columns remain valid JSONB values.
        audits.save(
            AuditEventEntity(
                actor = actor,
                action = action,
                resourceType = resourceType,
                resourceId = id,
                beforeState = before?.let(::jsonQuote),
                afterState = after?.let(::jsonQuote),
            ).apply {
                createdBy(actor)
            },
        )
    }

    private fun jsonQuote(value: String) = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
}
