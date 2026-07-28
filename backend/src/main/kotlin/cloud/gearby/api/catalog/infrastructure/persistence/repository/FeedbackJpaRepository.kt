package cloud.gearby.api.catalog.infrastructure.persistence.repository

import cloud.gearby.api.catalog.infrastructure.persistence.entity.FeedbackEntity
import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository

interface FeedbackJpaRepository : JpaRepository<FeedbackEntity, UUID> {
    fun findByOrderBySubmittedAtDescIdAsc(): List<FeedbackEntity>
}
