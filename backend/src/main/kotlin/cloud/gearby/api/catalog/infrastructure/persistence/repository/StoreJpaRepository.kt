package cloud.gearby.api.catalog.infrastructure.persistence.repository

import cloud.gearby.api.catalog.domain.StoreStatus
import cloud.gearby.api.catalog.infrastructure.persistence.entity.StoreEntity
import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository

interface StoreJpaRepository : JpaRepository<StoreEntity, UUID> {
    fun findByStatusOrderByNameAscIdAsc(status: StoreStatus): List<StoreEntity>
}
