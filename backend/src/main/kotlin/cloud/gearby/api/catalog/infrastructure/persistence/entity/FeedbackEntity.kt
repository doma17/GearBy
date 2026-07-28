package cloud.gearby.api.catalog.infrastructure.persistence.entity

import cloud.gearby.api.catalog.domain.FeedbackKind
import cloud.gearby.api.catalog.domain.FeedbackResolutionStatus
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
@Table(name = "feedback")
class FeedbackEntity(
    @Id var id: UUID = UUID.randomUUID(),
    @Column(name = "store_id") var storeId: UUID? = null,
    @Enumerated(EnumType.STRING) var kind: FeedbackKind = FeedbackKind.GENERAL,
    var content: String = "",
    @Column(name = "reply_email") var replyEmail: String? = null,
    @Column(name = "contact_consent") var contactConsent: Boolean = false,
    @Column(name = "submitted_at") var submittedAt: Instant = Instant.now(),
    @Enumerated(EnumType.STRING) @Column(name = "resolution_status") var resolutionStatus: FeedbackResolutionStatus = FeedbackResolutionStatus.PENDING,
    @Column(name = "resolution_summary") var resolutionSummary: String? = null,
    @Enumerated(EnumType.STRING) @Column(name = "notification_status") var notificationStatus: NotificationStatus = NotificationStatus.NOT_REQUESTED,
    @Column(name = "resolved_at") var resolvedAt: Instant? = null,
    @Column(name = "resolved_by") var resolvedBy: String? = null,
) : AuditableEntity()
