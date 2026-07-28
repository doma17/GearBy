package cloud.gearby.api.catalog.infrastructure.persistence.repository

import cloud.gearby.api.catalog.infrastructure.persistence.entity.CategoryReviewFlagEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface CategoryReviewFlagJpaRepository : JpaRepository<CategoryReviewFlagEntity, UUID>
