package no.nav.openinghours.model.db

import jakarta.persistence.*
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.*

@Entity
@Table(name = "service")
class Service(
    @Id
    @Column(nullable = false)
    var id: UUID = UUID.randomUUID(),

    @Column(nullable = false, length = 100)
    @NotBlank
    @Size(max = 100)
    var name: String = "",

    @Column(nullable = false, length = 20)
    @NotBlank
    @Size(max = 20)
    var type: String = "",

    @Column(nullable = false, length = 100)
    @NotBlank
    @Size(max = 100)
    var team: String = "",

    @Column(length = 300)
    var monitorlink: String? = null,

    @Column(length = 300)
    var logglink: String? = null,

    @Column(columnDefinition = "text")
    var description: String? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),

    @Column(name = "updated_at")
    var updatedAt: Instant? = null
)
