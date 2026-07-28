package cloud.gearby.api.catalog.infrastructure.persistence.repository

import cloud.gearby.api.catalog.infrastructure.persistence.entity.FeedbackNotificationAttemptEntity
import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository

interface FeedbackNotificationAttemptJpaRepository : JpaRepository<FeedbackNotificationAttemptEntity, UUID> {
    fun existsByFeedbackId(feedbackId: UUID): Boolean
    fun countByFeedbackId(feedbackId: UUID): Int
}
