package cloud.gearby.api.admin.request

import cloud.gearby.api.catalog.domain.Category
import cloud.gearby.api.catalog.domain.CategoryReviewFlagState
import cloud.gearby.api.catalog.domain.CorrectionTargetType
import cloud.gearby.api.catalog.domain.FeedbackResolutionStatus
import java.math.BigDecimal
import java.util.UUID

data class CategoryReviewFlagUpdateRequest(val state: CategoryReviewFlagState? = null, val assignee: String? = null, val resolution: String? = null)

data class CoordinatesRequest(val latitude: BigDecimal, val longitude: BigDecimal)

data class CorrectionRuleRequest(val source: String, val targetType: CorrectionTargetType, val target: String, val active: Boolean = true)

data class FeedbackResolutionRequest(val resolutionStatus: FeedbackResolutionStatus, val resolutionSummary: String)

data class ManualCategoryReviewFlagRequest(val storeId: UUID?, val reason: String)

data class StoreRejectionRequest(val reason: String? = null)

data class StoreRequest(
    val name: String,
    val address: String,
    val coordinates: CoordinatesRequest,
    val categories: Set<Category>,
    val phone: String? = null,
    val hours: String? = null,
    val description: String? = null,
)
