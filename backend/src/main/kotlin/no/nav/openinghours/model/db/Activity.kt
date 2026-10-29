package no.nav.openinghours.model.db

import jakarta.persistence.*
import org.hibernate.annotations.BatchSize
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "activity")
class Activity(
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    val id: UUID = UUID.randomUUID(),

    @ElementCollection
    @CollectionTable(name = "activity_service", joinColumns = [JoinColumn(name = "activity_id")])
    @Column(name = "service_id", nullable = false)
    @BatchSize(size = 100)
    var serviceIds: MutableSet<UUID> = mutableSetOf(),

    @Column(name = "header", nullable = false, length = 200)
    var header: String,

    @Column(name = "message", nullable = false, columnDefinition = "text")
    var message: String,

    @Column(name = "start_at", nullable = false)
    var startAt: Instant,

    @Column(name = "end_at", nullable = false)
    var endAt: Instant,

    @Column(name = "only_show_for_nav_employees", nullable = false)
    var onlyShowForNavEmployees: Boolean = false,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),

    @Column(name = "updated_at")
    var updatedAt: Instant? = null
) {
    @PrePersist fun onCreate() { createdAt = Instant.now(); updatedAt = null }
    @PreUpdate  fun onUpdate() { updatedAt = Instant.now() }

    companion object {
        fun create(
            id: UUID = UUID.randomUUID(),
            serviceIds: Set<UUID>,
            header: String,
            message: String,
            startAt: Instant,
            endAt: Instant,
            onlyShowForNavEmployees: Boolean = false
        ) = Activity(
            id = id,
            serviceIds = serviceIds.toMutableSet(),
            header = header,
            message = message,
            startAt = startAt,
            endAt = endAt,
            onlyShowForNavEmployees = onlyShowForNavEmployees
        )
    }
}
