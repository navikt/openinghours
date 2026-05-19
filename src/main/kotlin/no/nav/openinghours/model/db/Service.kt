package no.nav.openinghours.model.db

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

enum class ServiceType { TJENESTE, KOMPONENT }

@Entity
@Table(name = "service")
open class Service(
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    val id: UUID = UUID.randomUUID(),

    @Column(name = "name", nullable = false, length = 100)
    var name: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    var type: ServiceType,

    @Column(name = "team", nullable = false, length = 100)
    var team: String,

    @Column(name = "monitorlink", length = 300)
    var monitorlink: String? = null,

    @Column(name = "logglink", length = 300)
    var logglink: String? = null,

    @Column(name = "description", columnDefinition = "text")
    var description: String? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),

    @Column(name = "updated_at")
    var updatedAt: Instant? = null
) {
    protected constructor() : this(name = "", type = ServiceType.TJENESTE, team = "")

    @PrePersist fun onCreate() { createdAt = Instant.now(); updatedAt = null }
    @PreUpdate  fun onUpdate() { updatedAt = Instant.now() }

    companion object {
        fun create(
            id: UUID = UUID.randomUUID(),
            name: String,
            type: ServiceType,
            team: String,
            monitorlink: String? = null,
            logglink: String? = null,
            description: String? = null
        ) = Service(id, name, type, team, monitorlink, logglink, description)
    }
}
