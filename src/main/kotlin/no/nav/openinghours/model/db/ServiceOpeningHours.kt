package no.nav.openinghours.model.db

import jakarta.persistence.*
import java.time.Instant
import java.time.LocalTime
import java.util.*

@Entity
@Table(name = "service_opening_hours")
class ServiceOpeningHours(
    @Id
    @Column(nullable = false)
    var id: UUID = UUID.randomUUID(),

    @Column(name = "service_id", nullable = false)
    var serviceId: UUID = UUID.randomUUID(),

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id", insertable = false, updatable = false)
    var service: Service? = null,

    @Column(name = "day_of_the_week", nullable = false)
    var dayOfTheWeek: Int = 0,

    @Column(name = "opening_time", nullable = false)
    var openingTime: LocalTime = LocalTime.of(8, 0),

    @Column(name = "closing_time", nullable = false)
    var closingTime: LocalTime = LocalTime.of(16, 0),

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),

    @Column(name = "updated_at")
    var updatedAt: Instant? = null
)
