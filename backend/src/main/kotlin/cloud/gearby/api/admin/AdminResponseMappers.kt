package cloud.gearby.api.admin

import cloud.gearby.api.admin.response.AdminDashboardResponse
import cloud.gearby.api.admin.response.AdminFeedbackResponse
import cloud.gearby.api.admin.response.CategoryHealthResponse
import cloud.gearby.api.admin.response.CategoryReviewFlagResponse
import cloud.gearby.api.admin.response.CorrectionRuleResponse
import cloud.gearby.api.catalog.application.result.AdminCorrectionRuleResult
import cloud.gearby.api.catalog.application.result.AdminDashboardResult
import cloud.gearby.api.catalog.application.result.AdminFeedbackResult
import cloud.gearby.api.catalog.application.result.CategoryHealthResult
import cloud.gearby.api.catalog.application.result.CategoryReviewFlagResult

fun CategoryHealthResult.toResponse() = CategoryHealthResponse(category, publishedStoreCount, storesByLifecycle, openReviewFlagCount)

fun CategoryReviewFlagResult.toResponse() =
    CategoryReviewFlagResponse(
        id,
        storeId,
        storeName,
        source,
        sourceFeedbackId,
        reason,
        state,
        assignee,
        resolution,
        createdAt,
        resolvedAt,
        resolvedBy,
    )

fun AdminFeedbackResult.toResponse() =
    AdminFeedbackResponse(
        id,
        storeId,
        storeName,
        kind,
        content,
        contactConsent,
        submittedAt,
        resolutionStatus,
        resolutionSummary,
        notificationStatus,
    )

fun AdminCorrectionRuleResult.toResponse() = CorrectionRuleResponse(id, source, targetType, target, active)

fun AdminDashboardResult.toResponse() =
    AdminDashboardResponse(
        stores,
        feedback,
        activeCorrectionRules,
        categoryHealth.map {
            it.toResponse()
        },
        openCategoryReviewFlagCount,
    )
