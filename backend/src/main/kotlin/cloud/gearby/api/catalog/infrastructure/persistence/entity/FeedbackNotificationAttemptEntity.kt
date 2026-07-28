package cloud.gearby.api.catalog.infrastructure.persistence.entity

import cloud.gearby.api.catalog.domain.NotificationStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "feedback_notification_attempts")
class FeedbackNotificationAttemptEntity(
    @Id var id: UUID = UUID.randomUUID(),
    @Column(name = "feedback_id") var feedbackId: UUID = UUID.randomUUID(),
    @Column(name = "recipient_email") var recipientEmail: String = "",
    @Enumerated(EnumType.STRING) var status: NotificationStatus = NotificationStatus.QUEUED,
    @Column(name = "completed_at") var completedAt: Instant? = null,
) : AuditableEntity()
