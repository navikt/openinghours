package no.nav.openinghours.model.db

import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

interface SlackDowntimeMessageRepository : JpaRepository<SlackDowntimeMessage, UUID> {

    /** Newest message that is not a thread reply; replies are fetched separately and must not move the history cursor. */
    @Query(
        "select m from SlackDowntimeMessage m where m.channelId = :channelId " +
            "and (m.threadTs is null or m.threadTs = m.messageTs) order by m.postedAt desc, m.messageTs desc"
    )
    fun findNewestTopLevel(@Param("channelId") channelId: String, pageable: Pageable): List<SlackDowntimeMessage>

    @Query(
        "select m from SlackDowntimeMessage m where m.channelId = :channelId " +
            "and (m.threadTs is null or m.threadTs = m.messageTs) and m.postedAt >= :since order by m.postedAt"
    )
    fun findTopLevelPostedSince(@Param("channelId") channelId: String, @Param("since") since: Instant): List<SlackDowntimeMessage>

    @Query("select max(m.messageTs) from SlackDowntimeMessage m where m.channelId = :channelId and m.threadTs = :threadTs")
    fun findLatestTsInThread(@Param("channelId") channelId: String, @Param("threadTs") threadTs: String): String?

    fun findByChannelIdAndMessageTs(channelId: String, messageTs: String): SlackDowntimeMessage?

    @Query("select m from SlackDowntimeMessage m where :activityId member of m.activityIds")
    fun findLinkedTo(@Param("activityId") activityId: UUID): List<SlackDowntimeMessage>

    fun findByStatusOrderByPostedAtAsc(status: SlackDowntimeMessageStatus, pageable: Pageable): List<SlackDowntimeMessage>

    fun findAllByOrderByPostedAtDesc(pageable: Pageable): List<SlackDowntimeMessage>

    @Modifying
    @Query(
        value = """
            INSERT INTO slack_downtime_message (id, channel_id, message_ts, thread_ts, posted_at, raw_text, status, attempts, created_at)
            VALUES (:id, :channelId, :messageTs, :threadTs, :postedAt, :rawText, 'PENDING', 0, NOW())
            ON CONFLICT (channel_id, message_ts) DO NOTHING
        """,
        nativeQuery = true
    )
    fun insertIfAbsent(
        @Param("id") id: UUID,
        @Param("channelId") channelId: String,
        @Param("messageTs") messageTs: String,
        @Param("threadTs") threadTs: String?,
        @Param("postedAt") postedAt: Instant,
        @Param("rawText") rawText: String
    ): Int
}
