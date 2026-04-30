package no.nav.openinghours.model.db

import jakarta.persistence.*
import java.io.Serializable
import java.time.Instant
import java.util.*

@Embeddable
class ServiceOhGroupId(
    @Column(name = "service_id")
    var serviceId: UUID = UUID.randomUUID(),

    @Column(name = "group_id")
    var groupId: UUID = UUID.randomUUID()
) : Serializable {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ServiceOhGroupId) return false
        return serviceId == other.serviceId && groupId == other.groupId
    }

    override fun hashCode(): Int = Objects.hash(serviceId, groupId)
}

@Entity
@Table(name = "service_oh_group")
@IdClass(ServiceOhGroupId::class)
class ServiceOhGroup(
    @Id
    @Column(name = "service_id", nullable = false)
    var serviceId: UUID = UUID.randomUUID(),

    @Id
    @Column(name = "group_id", nullable = false)
    var groupId: UUID = UUID.randomUUID(),

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id", insertable = false, updatable = false)
    var service: Service? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", insertable = false, updatable = false)
    var ohGroup: OhGroup? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),

    @Column(name = "updated_at")
    var updatedAt: Instant? = null
)
