package no.nav.openinghours.model.db

import jakarta.persistence.*
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.*

@Entity
@Table(name = "service_status")
class ServiceStatus(
    @Id
    @Column(nullable = false)
    var id: UUID = UUID.randomUUID(),

    @Column(name = "service_id", nullable = false)
    var serviceId: UUID = UUID.randomUUID(),

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id", insertable = false, updatable = false)
    var service: Service? = null,

    @Column(nullable = false, length = 20)
    @NotBlank
    @Size(max = 20)
    var status: String = "",

    @Column(name = "response_time")
    var responseTime: Int? = null,

    @Column(length = 1000)
    var description: String? = null,

    @Column(length = 100)
    var logglink: String? = null,

    @Column(nullable = false, length = 20)
    @NotBlank
    @Size(max = 20)
    var source: String = "UNKNOWN",

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),

    @Column(name = "updated_at")
    var updatedAt: Instant? = null
)
