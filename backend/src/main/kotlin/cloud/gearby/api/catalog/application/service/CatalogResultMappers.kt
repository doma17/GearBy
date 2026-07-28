package cloud.gearby.api.catalog.application.service

import cloud.gearby.api.catalog.application.result.AdminCorrectionRuleResult
import cloud.gearby.api.catalog.application.result.AdminFeedbackResult
import cloud.gearby.api.catalog.application.result.CategoryReviewFlagResult
import cloud.gearby.api.catalog.application.result.StoreResult
import cloud.gearby.api.catalog.domain.Coordinates
import cloud.gearby.api.catalog.infrastructure.persistence.entity.CategoryReviewFlagEntity
import cloud.gearby.api.catalog.infrastructure.persistence.entity.CorrectionRuleEntity
import cloud.gearby.api.catalog.infrastructure.persistence.entity.FeedbackEntity
import cloud.gearby.api.catalog.infrastructure.persistence.entity.StoreEntity

fun StoreEntity.toResult() = StoreResult(id, name, address, Coordinates(latitude, longitude), categories.toSet(), phone, hours, description, status)
fun CorrectionRuleEntity.toResult() = AdminCorrectionRuleResult(id, source, targetType, target, active)
fun CategoryReviewFlagEntity.toResult(storeName: String?) = CategoryReviewFlagResult(id, storeId, storeName, source, sourceFeedbackId, reason, state, assignee, resolution, createdAt, resolvedAt, resolvedBy)
fun FeedbackEntity.toResult(storeName: String?) = AdminFeedbackResult(id, storeId, storeName, kind, content, contactConsent, submittedAt, resolutionStatus, resolutionSummary, notificationStatus)
