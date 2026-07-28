package cloud.gearby.api.catalog.infrastructure.implement

import cloud.gearby.api.catalog.application.command.CategoryReviewFlagUpdateCommand
import cloud.gearby.api.catalog.application.command.CorrectionRuleCommand
import cloud.gearby.api.catalog.application.command.FeedbackResolveCommand
import cloud.gearby.api.catalog.application.command.FeedbackSubmitCommand
import cloud.gearby.api.catalog.application.command.ManualCategoryReviewFlagCommand
import cloud.gearby.api.catalog.application.command.StoreUpsertCommand
import cloud.gearby.api.catalog.application.service.toResult
import cloud.gearby.api.catalog.domain.Category
import cloud.gearby.api.catalog.domain.CategoryReviewFlagSource
import cloud.gearby.api.catalog.domain.CategoryReviewFlagState
import cloud.gearby.api.catalog.domain.CorrectionTargetType
import cloud.gearby.api.catalog.domain.FeedbackResolutionStatus
import cloud.gearby.api.catalog.domain.NotificationStatus
import cloud.gearby.api.catalog.domain.StoreStatus
import cloud.gearby.api.catalog.infrastructure.persistence.entity.CategoryReviewFlagEntity
import cloud.gearby.api.catalog.infrastructure.persistence.entity.CorrectionRuleEntity
import cloud.gearby.api.catalog.infrastructure.persistence.entity.FeedbackEntity
import cloud.gearby.api.catalog.infrastructure.persistence.entity.StoreEntity
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import org.springframework.stereotype.Component

@Component
class CatalogManager(private val reader: CatalogReader, private val appender: CatalogAppender) {
    fun createStore(command: StoreUpsertCommand, actor: String): StoreEntity {
        validate(command)
        val store = StoreEntity(
            name = command.name.trim(), address = command.address.trim(), latitude = command.coordinates.latitude,
            longitude = command.coordinates.longitude, phone = command.phone.clean(), hours = command.hours.clean(), description = command.description.clean(),
        ).apply {
            categories.addAll(command.categories)
            createdBy(actor)
        }
        appender.saveStore(store)
        appender.audit("CREATE_DRAFT", store.id, actor, null, "DRAFT")
        return requireNotNull(reader.findStore(store.id))
    }

    fun updateStore(id: UUID, command: StoreUpsertCommand, actor: String): StoreEntity? {
        val store = reader.findStore(id) ?: return null
        if (store.status !in setOf(StoreStatus.DRAFT, StoreStatus.IN_REVIEW, StoreStatus.REJECTED)) return null
        if (store.status == StoreStatus.IN_REVIEW && command.categories.isEmpty()) {
            // Keep the review queue visible even when a reviewer submits an invalid category change.
            createCategoryReviewFlag(id, CategoryReviewFlagSource.VALIDATION, "A reviewed store requires at least one category", actor)
        }
        validate(command)
        val beforeCategories = store.categories.toSet()
        store.name = command.name.trim()
        store.address = command.address.trim()
        store.latitude = command.coordinates.latitude
        store.longitude = command.coordinates.longitude
        store.phone = command.phone.clean()
        store.hours = command.hours.clean()
        store.description = command.description.clean()
        store.categories.clear()
        store.categories.addAll(command.categories)
        store.touch(actor)
        appender.saveStore(store)
        appender.audit("UPDATE", id, actor, store.status.name, store.status.name)
        if (beforeCategories != command.categories) resolveCategoryReviewFlagsForCorrection(id, actor)
        return reader.findStore(id)
    }

    fun transitionStore(id: UUID, target: StoreStatus, actor: String, reason: String? = null): StoreEntity? {
        val store = reader.findStore(id) ?: return null
        if (!canTransition(store.status, target)) return null
        val before = store.status
        store.status = target
        store.touch(actor)
        appender.saveStore(store)
        appender.audit(target.name, id, actor, before.name, target.name)
        if (target == StoreStatus.REJECTED && reason.isCategoryReason()) createCategoryReviewFlag(id, CategoryReviewFlagSource.REJECTION, reason!!.trim(), actor)
        return reader.findStore(id)
    }

    fun submitFeedback(command: FeedbackSubmitCommand): FeedbackEntity {
        require(command.content.trim().length in 1..2000) { "content must be between 1 and 2000 characters" }
        val email = command.replyEmail.clean()
        require((email == null) == !command.contactConsent) { "replyEmail and contactConsent must be supplied together" }
        require(email == null || EMAIL.matches(email)) { "invalid replyEmail" }
        command.storeId?.let { require(reader.findStore(it)?.status == StoreStatus.PUBLISHED) { "store must be published" } }
        val item = FeedbackEntity(storeId = command.storeId, kind = command.kind, content = command.content.trim(), replyEmail = email, contactConsent = command.contactConsent).apply { createdBy("system") }
        appender.saveFeedback(item)
        if (command.categoryRelated && command.storeId != null) createCategoryReviewFlag(command.storeId, CategoryReviewFlagSource.FEEDBACK, "Category-related feedback", "system", item.id)
        return item
    }

    fun resolveFeedback(id: UUID, command: FeedbackResolveCommand, actor: String): FeedbackEntity? {
        require(command.resolutionStatus in setOf(FeedbackResolutionStatus.RESOLVED, FeedbackResolutionStatus.REJECTED)) { "resolution status must be RESOLVED or REJECTED" }
        val summary = command.resolutionSummary.trim()
        require(summary.isNotEmpty() && summary.length <= 1000) { "resolution summary is required and must be at most 1000 characters" }
        val item = reader.feedback(id) ?: return null
        if (item.resolutionStatus != FeedbackResolutionStatus.PENDING) return null
        val notification = if (item.contactConsent && item.replyEmail != null) NotificationStatus.QUEUED else NotificationStatus.NOT_REQUESTED
        item.resolutionStatus = command.resolutionStatus
        item.resolutionSummary = summary
        item.notificationStatus = notification
        item.resolvedAt = Instant.now()
        item.resolvedBy = actor
        item.touch(actor)
        appender.saveFeedback(item)
        // Notification attempts are persisted only for contacts that explicitly opted in.
        if (notification == NotificationStatus.QUEUED) appender.appendNotificationAttempt(id, item.replyEmail!!, actor)
        appender.audit("${command.resolutionStatus}_FEEDBACK", id, actor, FeedbackResolutionStatus.PENDING.name, command.resolutionStatus.name, "FEEDBACK")
        return reader.feedback(id)
    }

    fun createCorrectionRule(command: CorrectionRuleCommand, actor: String = "system"): CorrectionRuleEntity {
        val (source, target) = correctionRuleValues(command)
        require(reader.correctionRules().none { it.source == source }) { "correction rule already exists" }
        return appender.saveRule(CorrectionRuleEntity(source = source, targetType = command.targetType, target = target, active = command.active).apply { createdBy(actor) })
            .also { appender.audit("CREATE_CORRECTION_RULE", it.id, actor, null, it.toResult().toString(), "CORRECTION_RULE") }
    }

    fun updateCorrectionRule(id: UUID, command: CorrectionRuleCommand, actor: String): CorrectionRuleEntity? {
        val rule = reader.correctionRules().firstOrNull { it.id == id } ?: return null
        val before = rule.toResult().toString()
        val (source, target) = correctionRuleValues(command)
        require(reader.correctionRules().none { it.id != id && it.source == source }) { "correction rule already exists" }
        rule.source = source
        rule.targetType = command.targetType
        rule.target = target
        rule.active = command.active
        rule.touch(actor)
        appender.saveRule(rule)
        appender.audit("UPDATE_CORRECTION_RULE", id, actor, before, rule.toResult().toString(), "CORRECTION_RULE")
        return rule
    }

    fun deleteCorrectionRule(id: UUID, actor: String): Boolean {
        val rule = reader.correctionRules().firstOrNull { it.id == id } ?: return false
        appender.deleteRule(rule)
        appender.audit("DELETE_CORRECTION_RULE", id, actor, rule.toResult().toString(), null, "CORRECTION_RULE")
        return true
    }

    fun createManualCategoryReviewFlag(command: ManualCategoryReviewFlagCommand, actor: String): CategoryReviewFlagEntity {
        val storeId = requireNotNull(command.storeId) { "storeId is required" }
        require(reader.findStore(storeId) != null) { "store not found" }
        return createCategoryReviewFlag(storeId, CategoryReviewFlagSource.MANUAL, command.reason, actor)
    }

    fun updateCategoryReviewFlag(id: UUID, command: CategoryReviewFlagUpdateCommand, actor: String): CategoryReviewFlagEntity? {
        val flag = reader.categoryReviewFlags().firstOrNull { it.id == id } ?: return null
        val before = flag.state
        val state = command.state ?: flag.state
        val requestedAssignee = command.assignee.clean()
        require(requestedAssignee == null || requestedAssignee.length <= 200) { "assignee must be at most 200 characters" }
        val requestedResolution = command.resolution.clean()
        require(requestedResolution == null || requestedResolution.length <= 1000) { "resolution must be at most 1000 characters" }
        flag.assignee = requestedAssignee ?: flag.assignee
        flag.state = state
        flag.resolution = requestedResolution ?: flag.resolution ?: if (state == CategoryReviewFlagState.RESOLVED) "Resolved by admin" else null
        flag.resolvedAt = if (state == CategoryReviewFlagState.RESOLVED) flag.resolvedAt ?: Instant.now() else null
        flag.resolvedBy = if (state == CategoryReviewFlagState.RESOLVED) actor else null
        flag.touch(actor)
        appender.saveFlag(flag)
        appender.audit("UPDATE_CATEGORY_REVIEW_FLAG", id, actor, before.name, flag.state.name, "CATEGORY_REVIEW_FLAG")
        return reader.categoryReviewFlags().firstOrNull { it.id == id }
    }

    private fun createCategoryReviewFlag(storeId: UUID, source: CategoryReviewFlagSource, reason: String, actor: String, sourceFeedbackId: UUID? = null): CategoryReviewFlagEntity {
        val normalizedReason = reason.trim()
        require(normalizedReason.length in 1..500) { "category review reason is required" }
        reader.categoryReviewFlags(CategoryReviewFlagState.OPEN, storeId).firstOrNull { it.reason == normalizedReason }?.let { return it }
        val flag = CategoryReviewFlagEntity(storeId = storeId, source = source, sourceFeedbackId = sourceFeedbackId, reason = normalizedReason).apply { createdBy(actor) }
        appender.saveFlag(flag)
        appender.audit("CREATE_CATEGORY_REVIEW_FLAG", flag.id, actor, null, source.name, "CATEGORY_REVIEW_FLAG")
        return reader.categoryReviewFlags().first { it.id == flag.id }
    }

    private fun resolveCategoryReviewFlagsForCorrection(storeId: UUID, actor: String) {
        // Correcting categories resolves every open flag tied to the same store in one audit trail.
        reader.categoryReviewFlags(CategoryReviewFlagState.OPEN, storeId).forEach { flag ->
            flag.state = CategoryReviewFlagState.RESOLVED
            flag.resolution = "Store categories corrected"
            flag.resolvedAt = Instant.now()
            flag.resolvedBy = actor
            flag.touch(actor)
            appender.saveFlag(flag)
            appender.audit("RESOLVE_CATEGORY_REVIEW_FLAG", flag.id, actor, CategoryReviewFlagState.OPEN.name, CategoryReviewFlagState.RESOLVED.name, "CATEGORY_REVIEW_FLAG")
        }
    }

    private fun validate(command: StoreUpsertCommand) {
        require(command.name.isNotBlank() && command.address.isNotBlank()) { "name and address are required" }
        require(command.categories.isNotEmpty()) { "at least one reviewed category is required" }
        require(command.coordinates.latitude >= BigDecimal(-90) && command.coordinates.latitude <= BigDecimal(90)) { "invalid latitude" }
        require(command.coordinates.longitude >= BigDecimal(-180) && command.coordinates.longitude <= BigDecimal(180)) { "invalid longitude" }
    }

    private fun canTransition(source: StoreStatus, target: StoreStatus) = when (target) {
        StoreStatus.IN_REVIEW -> source in setOf(StoreStatus.DRAFT, StoreStatus.REJECTED)
        StoreStatus.PUBLISHED -> source == StoreStatus.IN_REVIEW
        StoreStatus.REJECTED -> source == StoreStatus.IN_REVIEW
        StoreStatus.DRAFT -> false
    }

    private fun correctionRuleValues(command: CorrectionRuleCommand): Pair<String, String> {
        val source = normalize(command.source)
        val target = command.target.trim()
        require(source.length in 1..120 && target.length in 1..200) { "source and target are required" }
        if (command.targetType == CorrectionTargetType.CATEGORY) require(runCatching { Category.valueOf(target) }.isSuccess) { "target must be a category" }
        return source to target
    }

    private fun String?.isCategoryReason() = this?.let { reason ->
        val normalized = reason.lowercase()
        normalized.contains("category") || Category.entries.any { normalized.contains(it.name.lowercase()) || normalized.contains(it.displayName.lowercase()) }
    } ?: false

    private fun String?.clean() = this?.trim()?.ifBlank { null }
    private fun normalize(value: String) = value.trim().lowercase()

    private companion object {
        val EMAIL = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
    }
}
