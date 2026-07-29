package cloud.gearby.api.admin.response

import cloud.gearby.api.catalog.api.response.CoordinatesResponse
import cloud.gearby.api.catalog.domain.Category
import cloud.gearby.api.catalog.domain.CategoryReviewFlagSource
import cloud.gearby.api.catalog.domain.CategoryReviewFlagState
import cloud.gearby.api.catalog.domain.CorrectionTargetType
import cloud.gearby.api.catalog.domain.FeedbackKind
import cloud.gearby.api.catalog.domain.FeedbackResolutionStatus
import cloud.gearby.api.catalog.domain.NotificationStatus
import cloud.gearby.api.catalog.domain.StoreInformationStatus
import java.time.Instant
import java.util.UUID

data class AdminDashboardResponse(
    val stores: Map<String, Int>,
    val feedback: Map<String, Int>,
    val activeCorrectionRules: Int,
    val categoryHealth: List<CategoryHealthResponse>,
    val openCategoryReviewFlagCount: Int,
)

data class AdminFeedbackResponse(
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

data class AdminStoreResponse(
    val id: UUID,
    val name: String,
    val address: String,
    val coordinates: CoordinatesResponse,
    val categories: List<String>,
    val phone: String?,
    val hours: String?,
    val description: String?,
    val status: String,
    val verifiedAt: Instant?,
    val informationStatus: StoreInformationStatus?,
)

data class CategoryHealthResponse(
    val category: Category,
    val publishedStoreCount: Int,
    val storesByLifecycle: Map<String, Int>,
    val openReviewFlagCount: Int,
)

data class CategoryReviewFlagResponse(
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

data class CorrectionRuleResponse(
    val id: UUID,
    val source: String,
    val targetType: CorrectionTargetType,
    val target: String,
    val active: Boolean,
)
