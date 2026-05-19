package no.nav.openinghours.model.db

import jakarta.persistence.*
import java.io.Serializable
import java.time.Instant
import java.util.UUID

@Embeddable
data class ServiceOhGroupId(
    @Column(name = "service_id", nullable = false) val serviceId: UUID = UUID.randomUUID(),
    @Column(name = "group_id", nullable = false) val groupId: UUID = UUID.randomUUID()
) : Serializable

@Entity
@Table(name = "service_oh_group")
open class ServiceOhGroup(
    @EmbeddedId
    val id: ServiceOhGroupId,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),

    @Column(name = "updated_at")
    var updatedAt: Instant? = null
) {
    protected constructor() : this(id = ServiceOhGroupId())

    @PrePersist
    fun onCreate() {
        createdAt = Instant.now()
        updatedAt = null
    }

    @PreUpdate
    fun onUpdate() {
        updatedAt = Instant.now()
    }
}
