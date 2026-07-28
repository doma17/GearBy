package cloud.gearby.api.catalog.infrastructure.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.MappedSuperclass
import jakarta.persistence.PrePersist
import jakarta.persistence.PreUpdate
import java.time.Instant

@MappedSuperclass
abstract class AuditableEntity {
    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now()

    @Column(name = "created_by", nullable = false)
    var createdBy: String = "system"

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()

    @Column(name = "edited_by", nullable = false)
    var editedBy: String = "system"

    fun touch(actor: String) {
        editedBy = actor
    }

    fun createdBy(actor: String) {
        createdBy = actor
        editedBy = actor
    }

    @PrePersist
    fun prePersist() {
        // Persistence lifecycle callbacks are the single source of creation timestamps.
        val now = Instant.now()
        createdAt = now
        updatedAt = now
    }

    @PreUpdate
    fun preUpdate() {
        updatedAt = Instant.now()
    }
}
