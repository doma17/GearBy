package cloud.gearby.api.catalog.infrastructure.persistence.entity

import cloud.gearby.api.catalog.domain.Category
import cloud.gearby.api.catalog.domain.StoreStatus
import jakarta.persistence.CollectionTable
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.Table
import java.math.BigDecimal
import java.util.UUID

@Entity
@Table(name = "stores")
class StoreEntity(
    @Id var id: UUID = UUID.randomUUID(),
    var name: String = "",
    @Column(name = "normalized_address") var address: String = "",
    var latitude: BigDecimal = BigDecimal.ZERO,
    var longitude: BigDecimal = BigDecimal.ZERO,
    var phone: String? = null,
    var hours: String? = null,
    var description: String? = null,
    @Enumerated(EnumType.STRING) var status: StoreStatus = StoreStatus.DRAFT,
) : AuditableEntity() {
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "store_categories", joinColumns = [JoinColumn(name = "store_id")])
    @Column(name = "category_slug")
    @Enumerated(EnumType.STRING)
    var categories: MutableSet<Category> = mutableSetOf()
}
