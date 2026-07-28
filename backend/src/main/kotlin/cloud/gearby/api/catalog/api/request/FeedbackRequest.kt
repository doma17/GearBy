package cloud.gearby.api.catalog.api.request

import cloud.gearby.api.catalog.domain.FeedbackKind
import java.util.UUID

data class FeedbackRequest(
    val kind: FeedbackKind,
    val storeId: UUID? = null,
    val content: String,
    val replyEmail: String? = null,
    val contactConsent: Boolean = false,
    val categoryRelated: Boolean = false,
)
