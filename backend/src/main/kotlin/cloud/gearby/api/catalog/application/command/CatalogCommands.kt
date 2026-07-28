package cloud.gearby.api.catalog.application.command

import cloud.gearby.api.catalog.domain.Category
import cloud.gearby.api.catalog.domain.CategoryReviewFlagState
import cloud.gearby.api.catalog.domain.Coordinates
import cloud.gearby.api.catalog.domain.CorrectionTargetType
import cloud.gearby.api.catalog.domain.FeedbackKind
import cloud.gearby.api.catalog.domain.FeedbackResolutionStatus
import java.util.UUID

data class CategoryReviewFlagUpdateCommand(
    val state: CategoryReviewFlagState? = null,
    val assignee: String? = null,
    val resolution: String? = null,
)

data class CorrectionRuleCommand(
    val source: String,
    val targetType: CorrectionTargetType,
    val target: String,
    val active: Boolean = true,
)

data class FeedbackResolveCommand(
    val resolutionStatus: FeedbackResolutionStatus,
    val resolutionSummary: String,
)

data class FeedbackSubmitCommand(
    val kind: FeedbackKind,
    val storeId: UUID? = null,
    val content: String,
    val replyEmail: String? = null,
    val contactConsent: Boolean = false,
    val categoryRelated: Boolean = false,
)

data class ManualCategoryReviewFlagCommand(val storeId: UUID?, val reason: String)

data class StoreUpsertCommand(
    val name: String,
    val address: String,
    val coordinates: Coordinates,
    val categories: Set<Category>,
    val phone: String? = null,
    val hours: String? = null,
    val description: String? = null,
)
