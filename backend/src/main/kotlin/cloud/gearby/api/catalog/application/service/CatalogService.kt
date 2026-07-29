package cloud.gearby.api.catalog.application.service

import cloud.gearby.api.catalog.application.command.CategoryReviewFlagUpdateCommand
import cloud.gearby.api.catalog.application.command.CorrectionRuleCommand
import cloud.gearby.api.catalog.application.command.FeedbackResolveCommand
import cloud.gearby.api.catalog.application.command.FeedbackSubmitCommand
import cloud.gearby.api.catalog.application.command.ManualCategoryReviewFlagCommand
import cloud.gearby.api.catalog.application.command.StoreUpsertCommand
import cloud.gearby.api.catalog.application.query.StoreQuery
import cloud.gearby.api.catalog.application.result.AdminCorrectionRuleResult
import cloud.gearby.api.catalog.application.result.AdminDashboardResult
import cloud.gearby.api.catalog.application.result.AdminFeedbackResult
import cloud.gearby.api.catalog.application.result.AuditEventResult
import cloud.gearby.api.catalog.application.result.CategoryHealthResult
import cloud.gearby.api.catalog.application.result.CategoryReviewFlagResult
import cloud.gearby.api.catalog.application.result.FeedbackReceiptResult
import cloud.gearby.api.catalog.application.result.SearchDisclosureResult
import cloud.gearby.api.catalog.application.result.StorePageResult
import cloud.gearby.api.catalog.application.result.StoreResult
import cloud.gearby.api.catalog.domain.Category
import cloud.gearby.api.catalog.domain.CategoryReviewFlagState
import cloud.gearby.api.catalog.domain.Coordinates
import cloud.gearby.api.catalog.domain.CorrectionTargetType
import cloud.gearby.api.catalog.domain.FeedbackResolutionStatus
import cloud.gearby.api.catalog.domain.StoreStatus
import cloud.gearby.api.catalog.infrastructure.implement.CatalogManager
import cloud.gearby.api.catalog.infrastructure.implement.CatalogReader
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Duration
import java.util.Base64
import java.util.UUID
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Combines catalog data into application-level results. */
@Service
class CatalogService(
    private val reader: CatalogReader,
    private val manager: CatalogManager,
    private val clock: Clock = Clock.systemUTC(),
    @Value("\${gearby.catalog.review-period:P180D}") private val reviewPeriod: Duration = Duration.ofDays(180),
) {
    fun categories(): List<Category> = reader.categories()

    fun published(): List<StoreResult> = reader.storesByStatus(StoreStatus.PUBLISHED).map { it.toResult(clock, reviewPeriod) }

    fun find(id: UUID): StoreResult? = reader.findStore(id)?.toResult(clock, reviewPeriod)

    fun findByStatus(status: StoreStatus): List<StoreResult> = reader.storesByStatus(status).map { it.toResult(clock, reviewPeriod) }

    fun search(query: StoreQuery): StorePageResult {
        require(query.sort in setOf("name", "distance")) { "invalid sort" }
        require(query.limit in 1..100) { "limit must be between 1 and 100" }
        require(query.sort != "distance" || query.near != null) { "near is required when sort is distance" }
        query.bbox?.let { require(it.minLongitude <= it.maxLongitude && it.minLatitude <= it.maxLatitude) { "invalid bbox" } }

        val original = query.query?.trim()?.takeIf(String::isNotEmpty)
        var applied = original
        var correction: String? = null
        var stores = published()
        if (query.categories.isNotEmpty()) stores = stores.filter { it.categories.any(query.categories::contains) }
        query.bbox?.let { box ->
            stores =
                stores.filter {
                    it.coordinates.longitude in box.minLongitude..box.maxLongitude &&
                        it.coordinates.latitude in box.minLatitude..box.maxLatitude
                }
        }
        if (original != null) {
            val direct = stores.filter { it.matches(original) }
            // Preserve an exact user match before applying an operator-maintained correction rule.
            if (direct.isNotEmpty()) {
                stores = direct
            } else if (query.applyCorrection) {
                reader.correctionFor(normalize(original))?.let { rule ->
                    applied = rule.target
                    correction = "$original → ${rule.target}"
                    stores =
                        when (rule.targetType) {
                            CorrectionTargetType.CATEGORY -> stores.filter { Category.valueOf(rule.target) in it.categories }
                            CorrectionTargetType.STORE -> stores.filter { it.matches(rule.target) }
                        }
                } ?: run { stores = emptyList() }
            } else {
                stores = emptyList()
            }
        }
        stores =
            if (query.sort ==
                "distance"
            ) {
                stores.sortedWith(compareBy<StoreResult> { it.distanceTo(query.near!!) }.thenBy { it.name }.thenBy { it.id })
            } else {
                stores.sortedWith(compareBy<StoreResult> { it.name }.thenBy { it.id })
            }
        // The opaque cursor carries only an offset, so validate it against the filtered result set.
        val offset = decodeCursor(query.cursor)
        require(offset <= stores.size) { "invalid cursor" }
        val items = stores.drop(offset).take(query.limit)
        val next = (offset + items.size).takeIf { it < stores.size }?.let(::encodeCursor)
        return StorePageResult(items, next, original?.let { SearchDisclosureResult(it, applied ?: it, correction) })
    }

    @Transactional fun create(
        command: StoreUpsertCommand,
        actor: String,
    ): StoreResult = manager.createStore(command, actor).toResult(clock, reviewPeriod)

    @Transactional(noRollbackFor = [IllegalArgumentException::class])
    fun update(
        id: UUID,
        command: StoreUpsertCommand,
        actor: String,
    ): StoreResult? = manager.updateStore(id, command, actor)?.toResult(clock, reviewPeriod)

    @Transactional fun transition(
        id: UUID,
        target: StoreStatus,
        actor: String,
        reason: String? = null,
    ): StoreResult? = manager.transitionStore(id, target, actor, reason)?.toResult(clock, reviewPeriod)

    @Transactional fun submitFeedback(command: FeedbackSubmitCommand): FeedbackReceiptResult =
        FeedbackReceiptResult(
            manager.submitFeedback(command).id,
        )

    @Transactional fun resolveFeedback(
        id: UUID,
        command: FeedbackResolveCommand,
        actor: String,
    ): AdminFeedbackResult? =
        manager.resolveFeedback(id, command, actor)?.let {
            feedback().first { result ->
                result.id ==
                    it.id
            }
        }

    @Transactional fun createCorrectionRule(
        command: CorrectionRuleCommand,
        actor: String = "system",
    ): AdminCorrectionRuleResult = manager.createCorrectionRule(command, actor).toResult()

    @Transactional fun updateCorrectionRule(
        id: UUID,
        command: CorrectionRuleCommand,
        actor: String,
    ): AdminCorrectionRuleResult? = manager.updateCorrectionRule(id, command, actor)?.toResult()

    @Transactional fun deleteCorrectionRule(
        id: UUID,
        actor: String,
    ): Boolean = manager.deleteCorrectionRule(id, actor)

    @Transactional fun createManualCategoryReviewFlag(
        command: ManualCategoryReviewFlagCommand,
        actor: String,
    ): CategoryReviewFlagResult = manager.createManualCategoryReviewFlag(command, actor).toResult(reader.findStore(command.storeId!!)?.name)

    @Transactional fun updateCategoryReviewFlag(
        id: UUID,
        command: CategoryReviewFlagUpdateCommand,
        actor: String,
    ): CategoryReviewFlagResult? =
        manager.updateCategoryReviewFlag(id, command, actor)?.let {
            it.toResult(reader.findStore(it.storeId)?.name)
        }

    fun categoryReviewFlags(
        state: CategoryReviewFlagState? = null,
        storeId: UUID? = null,
        assignee: String? = null,
    ): List<CategoryReviewFlagResult> =
        reader.categoryReviewFlags(state, storeId, assignee).map {
            it.toResult(reader.findStore(it.storeId)?.name)
        }

    fun correctionRules(): List<AdminCorrectionRuleResult> = reader.correctionRules().map { it.toResult() }

    fun feedback(): List<AdminFeedbackResult> = reader.feedback().map { it.toResult(reader.findStore(it.storeId ?: UUID(0, 0))?.name) }

    fun notificationAttempts(id: UUID): Int = reader.notificationAttempts(id)

    fun auditEvents(id: UUID): List<AuditEventResult> =
        reader.auditEvents(id).map {
            AuditEventResult(it.action, it.resourceId, it.actor, it.createdAt)
        }

    fun dashboard(): AdminDashboardResult {
        val storeCounts = StoreStatus.entries.associate { it.name to reader.storesByStatus(it).size }
        val feedbackCounts =
            FeedbackResolutionStatus.entries.associate { status ->
                status.name to
                    reader.feedback().count { it.resolutionStatus == status }
            }
        val categoryHealth = categoryHealth()
        return AdminDashboardResult(
            storeCounts,
            feedbackCounts,
            reader.correctionRules().count {
                it.active
            },
            categoryHealth,
            categoryReviewFlags(CategoryReviewFlagState.OPEN).size,
        )
    }

    fun categoryHealth(): List<CategoryHealthResult> =
        Category.entries.map { category ->
            val stores = reader.allStores().filter { category in it.categories }
            CategoryHealthResult(
                category,
                stores.count { it.status == StoreStatus.PUBLISHED },
                StoreStatus.entries.associate { status -> status.name to stores.count { it.status == status } },
                reader.categoryReviewFlags(CategoryReviewFlagState.OPEN).count { flag ->
                    reader.findStore(flag.storeId)?.categories?.contains(category) ==
                        true
                },
            )
        }

    private fun StoreResult.matches(query: String): Boolean {
        val normalized = normalize(query)
        return listOf(
            name,
            address,
            description.orEmpty(),
            *categories.flatMap { listOf(it.name, it.displayName) }.toTypedArray(),
        ).any { normalize(it).contains(normalized) }
    }

    private fun StoreResult.distanceTo(near: Coordinates): Double {
        val latitudeDelta = Math.toRadians(coordinates.latitude.subtract(near.latitude).toDouble())
        val longitudeDelta = Math.toRadians(coordinates.longitude.subtract(near.longitude).toDouble())
        val a =
            sin(latitudeDelta / 2).pow(2) +
                cos(Math.toRadians(near.latitude.toDouble())) * cos(Math.toRadians(coordinates.latitude.toDouble())) *
                sin(longitudeDelta / 2).pow(2)
        return 6_371_000 * 2 * asin(sqrt(a))
    }

    private fun encodeCursor(offset: Int) = Base64.getUrlEncoder().withoutPadding().encodeToString(offset.toString().toByteArray())

    private fun decodeCursor(cursor: String?): Int =
        cursor?.let {
            runCatching {
                Base64
                    .getUrlDecoder()
                    .decode(
                        it,
                    ).decodeToString()
                    .toInt()
            }.getOrElse { throw IllegalArgumentException("invalid cursor") }
        }
            ?: 0

    private fun normalize(value: String) = value.trim().lowercase()
}
