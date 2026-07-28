package cloud.gearby.api.catalog.application.result

import cloud.gearby.api.catalog.domain.Category
import cloud.gearby.api.catalog.domain.CategoryReviewFlagSource
import cloud.gearby.api.catalog.domain.CategoryReviewFlagState
import cloud.gearby.api.catalog.domain.Coordinates
import cloud.gearby.api.catalog.domain.CorrectionTargetType
import cloud.gearby.api.catalog.domain.FeedbackKind
import cloud.gearby.api.catalog.domain.FeedbackResolutionStatus
import cloud.gearby.api.catalog.domain.NotificationStatus
import cloud.gearby.api.catalog.domain.StoreStatus
import java.time.Instant
import java.util.UUID

data class AdminCorrectionRuleResult(
    val id: UUID,
    val source: String,
    val targetType: CorrectionTargetType,
    val target: String,
    val active: Boolean,
)

data class AdminDashboardResult(
    val stores: Map<String, Int>,
    val feedback: Map<String, Int>,
    val activeCorrectionRules: Int,
    val categoryHealth: List<CategoryHealthResult>,
    val openCategoryReviewFlagCount: Int,
)

data class AdminFeedbackResult(
    val id: UUID,
    val storeId: UUID?,
    val storeName: String?,
    val kind: FeedbackKind,
    val content: String,
    val contactConsent: Boolean,
    val submittedAt: Instant,
    val resolutionStatus: FeedbackResolutionStatus,
    val resolutionSummary: String?,
    val notificationStatus: NotificationStatus,
)

data class AuditEventResult(
    val action: String,
    val resourceId: UUID,
    val actor: String,
    val createdAt: Instant,
)

data class CategoryHealthResult(
    val category: Category,
    val publishedStoreCount: Int,
    val storesByLifecycle: Map<String, Int>,
    val openReviewFlagCount: Int,
)

data class CategoryReviewFlagResult(
    val id: UUID,
    val storeId: UUID?,
    val storeName: String?,
    val source: CategoryReviewFlagSource,
    val sourceFeedbackId: UUID?,
    val reason: String,
    val state: CategoryReviewFlagState,
    val assignee: String?,
    val resolution: String?,
    val createdAt: Instant,
    val resolvedAt: Instant?,
    val resolvedBy: String?,
)

data class FeedbackReceiptResult(
    val id: UUID,
    val status: String = "ACCEPTED",
)

data class SearchDisclosureResult(
    val originalQuery: String,
    val appliedQuery: String,
    val correction: String?,
)

data class StorePageResult(
    val items: List<StoreResult>,
    val nextCursor: String?,
    val search: SearchDisclosureResult?,
)

data class StoreResult(
    val id: UUID,
    val name: String,
    val address: String,
    val coordinates: Coordinates,
    val categories: Set<Category>,
    val phone: String?,
    val hours: String?,
    val description: String?,
    val status: StoreStatus? = null,
)
