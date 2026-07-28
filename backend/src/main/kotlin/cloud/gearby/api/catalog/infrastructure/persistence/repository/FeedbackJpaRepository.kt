package cloud.gearby.api.catalog.infrastructure.persistence.repository

import cloud.gearby.api.catalog.infrastructure.persistence.entity.FeedbackEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface FeedbackJpaRepository : JpaRepository<FeedbackEntity, UUID> {
    fun findByOrderBySubmittedAtDescIdAsc(): List<FeedbackEntity>
}
