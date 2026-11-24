package no.nav.openinghours.model.db

import jakarta.persistence.*
import org.hibernate.annotations.BatchSize
import java.time.Instant
import java.util.UUID

/**
 * ACTIVITY_UPDATED and ACTIVITY_CANCELLED are set on follow-up messages (completed, cancelled or
 * postponed) that changed or deleted activities created from earlier messages.
 */
enum class SlackDowntimeMessageStatus { PENDING, ACTIVITY_CREATED, ACTIVITY_UPDATED, ACTIVITY_CANCELLED, NOT_RELEVANT, FAILED }

@Entity
@Table(name = "slack_downtime_message")
class SlackDowntimeMessage(
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    val id: UUID = UUID.randomUUID(),

    @Column(name = "channel_id", nullable = false, length = 50)
    val channelId: String,

    @Column(name = "message_ts", nullable = false, length = 50)
    val messageTs: String,

    /** Parent message ts for thread replies; equal to [messageTs] for a thread parent, null otherwise. */
    @Column(name = "thread_ts", length = 50)
    val threadTs: String? = null,

    @Column(name = "posted_at", nullable = false)
    val postedAt: Instant,

    @Column(name = "raw_text", nullable = false, columnDefinition = "text")
    val rawText: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    var status: SlackDowntimeMessageStatus = SlackDowntimeMessageStatus.PENDING,

    @Column(name = "extraction", columnDefinition = "text")
    var extraction: String? = null,

    @Column(name = "reason", columnDefinition = "text")
    var reason: String? = null,

    @Column(name = "attempts", nullable = false)
    var attempts: Int = 0,

    @ElementCollection
    @CollectionTable(name = "slack_downtime_message_activity", joinColumns = [JoinColumn(name = "message_id")])
    @Column(name = "activity_id", nullable = false)
    @BatchSize(size = 100)
    var activityIds: MutableSet<UUID> = mutableSetOf(),

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),

    @Column(name = "updated_at")
    var updatedAt: Instant? = null
) {
    val isReply: Boolean get() = threadTs != null && threadTs != messageTs

    @PrePersist fun onCreate() { createdAt = Instant.now(); updatedAt = null }
    @PreUpdate  fun onUpdate() { updatedAt = Instant.now() }
}
