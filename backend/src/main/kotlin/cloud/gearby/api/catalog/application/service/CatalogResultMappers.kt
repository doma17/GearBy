package cloud.gearby.api.catalog.application.service

import cloud.gearby.api.catalog.application.result.AdminCorrectionRuleResult
import cloud.gearby.api.catalog.application.result.AdminFeedbackResult
import cloud.gearby.api.catalog.application.result.CategoryReviewFlagResult
import cloud.gearby.api.catalog.application.result.StoreResult
import cloud.gearby.api.catalog.domain.Coordinates
import cloud.gearby.api.catalog.domain.StoreInformationStatus
import cloud.gearby.api.catalog.infrastructure.persistence.entity.CategoryReviewFlagEntity
import cloud.gearby.api.catalog.infrastructure.persistence.entity.CorrectionRuleEntity
import cloud.gearby.api.catalog.infrastructure.persistence.entity.FeedbackEntity
import cloud.gearby.api.catalog.infrastructure.persistence.entity.StoreEntity
import java.time.Clock
import java.time.Duration

fun StoreEntity.toResult(
    clock: Clock,
    reviewPeriod: Duration,
) = StoreResult(
    id,
    name,
    address,
    Coordinates(latitude, longitude),
    categories.toSet(),
    phone,
    hours,
    description,
    status,
    verifiedAt,
    verifiedAt?.let {
        if (clock.instant().isBefore(it.plus(reviewPeriod))) StoreInformationStatus.VERIFIED else StoreInformationStatus.REVIEW_DUE
    },
)

fun CorrectionRuleEntity.toResult() = AdminCorrectionRuleResult(id, source, targetType, target, active)

fun CategoryReviewFlagEntity.toResult(storeName: String?) =
    CategoryReviewFlagResult(
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

fun FeedbackEntity.toResult(storeName: String?) =
    AdminFeedbackResult(
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
