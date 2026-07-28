package cloud.gearby.api.catalog.infrastructure.persistence.repository

import cloud.gearby.api.catalog.infrastructure.persistence.entity.FeedbackNotificationAttemptEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface FeedbackNotificationAttemptJpaRepository : JpaRepository<FeedbackNotificationAttemptEntity, UUID> {
    fun existsByFeedbackId(feedbackId: UUID): Boolean

    fun countByFeedbackId(feedbackId: UUID): Int
}
