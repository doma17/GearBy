package cloud.gearby.api.catalog.infrastructure.persistence.repository

import cloud.gearby.api.catalog.infrastructure.persistence.entity.CategoryReviewFlagEntity
import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository

interface CategoryReviewFlagJpaRepository : JpaRepository<CategoryReviewFlagEntity, UUID>
