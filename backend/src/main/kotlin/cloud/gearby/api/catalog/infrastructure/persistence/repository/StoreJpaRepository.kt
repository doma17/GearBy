package cloud.gearby.api.catalog.infrastructure.persistence.repository

import cloud.gearby.api.catalog.domain.StoreStatus
import cloud.gearby.api.catalog.infrastructure.persistence.entity.StoreEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface StoreJpaRepository : JpaRepository<StoreEntity, UUID> {
    fun findByStatusOrderByNameAscIdAsc(status: StoreStatus): List<StoreEntity>
}
