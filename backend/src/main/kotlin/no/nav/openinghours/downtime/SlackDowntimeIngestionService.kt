package no.nav.openinghours.downtime

import no.nav.openinghours.model.db.ActivityRepository
import no.nav.openinghours.model.db.ActivitySource
import no.nav.openinghours.model.db.ServiceRepository
import no.nav.openinghours.model.db.SlackDowntimeMessage
import no.nav.openinghours.model.db.SlackDowntimeMessageRepository
import no.nav.openinghours.model.db.SlackDowntimeMessageStatus
import no.nav.openinghours.model.db.SlackDowntimeMessageStatus.*
import no.nav.openinghours.service.ActivityInput
import no.nav.openinghours.service.ActivityService
import no.nav.openinghours.slack.SlackClient
import no.nav.openinghours.slack.SlackHistoryMessage
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.client.RestClientException
import org.springframework.web.server.ResponseStatusException
import java.math.BigDecimal
import java.time.Clock
import java.time.DateTimeException
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.util.UUID

data class SlackDowntimePollResult(
    val fetched: Int,
    val activityCreated: Int,
    val notRelevant: Int,
    val failed: Int,
    val activityUpdated: Int = 0,
    val activityCancelled: Int = 0
)

data class SlackDowntimeMessageView(
    val id: UUID,
    val channelId: String,
    val messageTs: String,
    val threadTs: String?,
    val postedAt: OffsetDateTime,
    val rawText: String,
    val status: SlackDowntimeMessageStatus,
    val extraction: String?,
    val reason: String?,
    val attempts: Int,
    val activityIds: List<UUID>
)

@Service
class SlackDowntimeIngestionService(
    private val slack: SlackClient,
    private val extractor: DowntimeExtractor,
    private val repo: SlackDowntimeMessageRepository,
    private val serviceRepo: ServiceRepository,
    private val activityService: ActivityService,
    private val activityRepo: ActivityRepository,
    private val followUps: DowntimeFollowUpHandler,
    transactionManager: PlatformTransactionManager,
    private val clock: Clock,
    @Value("\${slack.downtime.channel-id:}") private val channelId: String,
    @Value("\${slack.downtime.lookback-hours:24}") lookbackHours: Long,
    @Value("\${slack.downtime.max-attempts:3}") private val maxAttempts: Int,
    @Value("\${slack.downtime.batch-size:20}") private val batchSize: Int,
    @Value("\${slack.downtime.follow-up-window-hours:24}") followUpWindowHours: Long,
    @Value("\${slack.downtime.only-show-for-nav-employees:true}") private val onlyShowForNavEmployees: Boolean
) {
    private val log = LoggerFactory.getLogger(SlackDowntimeIngestionService::class.java)
    private val transactions = TransactionTemplate(transactionManager)
    private val lookback = Duration.ofHours(lookbackHours)
    private val threadWindow = Duration.ofHours(followUpWindowHours)

    companion object {
        const val DEFAULT_HEADER = "Planlagt nedetid"
        private val ANNOUNCEMENT_SUBTYPES = setOf(null, "bot_message", "thread_broadcast")
        private val APPLIED = setOf(ACTIVITY_CREATED, ACTIVITY_UPDATED, ACTIVITY_CANCELLED)

        fun tsToInstant(ts: String): Instant {
            val value = BigDecimal(ts)
            val seconds = value.toBigInteger().toLong()
            val nanos = value.subtract(BigDecimal(seconds)).movePointRight(9).toInt()
            return Instant.ofEpochSecond(seconds, nanos.toLong())
        }

        fun instantToTs(instant: Instant): String = "%d.%06d".format(instant.epochSecond, instant.nano / 1000)
    }

    val enabled: Boolean get() = slack.hasToken && channelId.isNotBlank() && extractor.enabled

    @Synchronized
    fun poll(): SlackDowntimePollResult {
        if (!enabled) {
            log.debug("Slack downtime ingestion is disabled")
            return SlackDowntimePollResult(0, 0, 0, 0)
        }
        val fetched = fetchNewMessages()
        val statuses = processPending()
        return SlackDowntimePollResult(
            fetched = fetched,
            activityCreated = statuses.count { it == ACTIVITY_CREATED },
            notRelevant = statuses.count { it == NOT_RELEVANT },
            failed = statuses.count { it == FAILED },
            activityUpdated = statuses.count { it == ACTIVITY_UPDATED },
            activityCancelled = statuses.count { it == ACTIVITY_CANCELLED }
        ).also { log.info("Slack downtime poll finished result={}", it) }
    }

    @Transactional(readOnly = true)
    fun list(limit: Int): List<SlackDowntimeMessageView> =
        repo.findAllByOrderByPostedAtDesc(PageRequest.of(0, limit.coerceIn(1, 500))).map(::toView)

    /** Puts a failed or not-relevant message back in the queue so it is extracted again on the next poll. */
    @Transactional
    fun retry(id: UUID): SlackDowntimeMessageView {
        val entity = repo.findById(id).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Slack downtime message not found: $id")
        }
        if (entity.status in APPLIED) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "This message has already been applied to activities")
        }
        entity.status = PENDING
        entity.attempts = 0
        entity.reason = null
        return toView(repo.saveAndFlush(entity))
    }

    fun fetchNewMessages(): Int {
        val oldest = repo.findNewestTopLevel(channelId, PageRequest.of(0, 1)).firstOrNull()?.messageTs
            ?: instantToTs(clock.instant().minus(lookback))
        return store(slack.fetchChannelHistory(channelId, oldest)) + fetchThreadReplies()
    }

    /** Fetches new replies to top-level messages posted within the follow-up window. */
    fun fetchThreadReplies(): Int =
        repo.findTopLevelPostedSince(channelId, clock.instant().minus(threadWindow)).sumOf { parent ->
            val oldest = repo.findLatestTsInThread(channelId, parent.messageTs) ?: parent.messageTs
            try {
                store(slack.fetchThreadReplies(channelId, parent.messageTs, oldest), parent.messageTs)
            } catch (e: RestClientException) {
                log.warn("Fetching replies to Slack message ts={} failed: {}", parent.messageTs, e.message)
                0
            }
        }

    fun store(messages: List<SlackHistoryMessage>, threadTs: String? = null): Int = transactions.execute {
        messages.filter { it.subtype in ANNOUNCEMENT_SUBTYPES && it.ts.toBigDecimalOrNull() != null }
            .mapNotNull { m -> m.fullText().takeIf { it.isNotBlank() }?.let { m to it } }
            .sumOf { (m, text) ->
                repo.insertIfAbsent(UUID.randomUUID(), channelId, m.ts, m.threadTs ?: threadTs, tsToInstant(m.ts), text)
            }
    } ?: 0

    /**
     * Processes pending messages oldest first and stops at the first one that stays pending, so a later follow-up
     * never runs before the announcement it refers to. Each message is retried at most [maxAttempts] times, so the
     * queue is held back only for a bounded number of polls.
     */
    fun processPending(): List<SlackDowntimeMessageStatus> {
        val pending = repo.findByStatusOrderByPostedAtAsc(PENDING, PageRequest.of(0, batchSize))
        if (pending.isEmpty()) return emptyList()
        val services = serviceRepo.findAll()
        val statuses = mutableListOf<SlackDowntimeMessageStatus>()
        for (message in pending) {
            statuses += process(message, services)
            if (statuses.last() == PENDING) break
        }
        return statuses
    }

    private fun process(message: SlackDowntimeMessage, services: List<no.nav.openinghours.model.db.Service>): SlackDowntimeMessageStatus {
        val parent = message.threadTs?.takeIf { message.isReply }?.let { repo.findByChannelIdAndMessageTs(message.channelId, it) }
        if (parent?.status == PENDING) {
            // The parent may still create activities this reply refers to; try again once it has been processed.
            log.debug("Deferring Slack reply id={} until its parent id={} has been processed", message.id, parent.id)
            return PENDING
        }
        val result = try {
            extractor.extract(message.rawText, message.postedAt, services, parent?.rawText)
        } catch (e: RestClientException) {
            return recordFailure(message.id, null, "Extraction failed: ${e.message}")
        }
        val catalog = services.associateBy { it.id }
        val kind = result.extraction.messageKind
        return try {
            transactions.execute {
                val entity = repo.findById(message.id).orElseThrow()
                entity.extraction = result.raw
                entity.attempts++
                val outcome = when (kind) {
                    MessageKind.ANNOUNCEMENT -> announce(entity, result.extraction, catalog)
                    MessageKind.COMPLETION, MessageKind.CANCELLATION, MessageKind.POSTPONEMENT ->
                        followUps.apply(kind, entity, parent?.let { repo.findById(it.id).orElse(null) }, result.extraction, catalog)
                    MessageKind.OTHER -> DowntimeOutcome(NOT_RELEVANT, emptyList())
                }
                entity.status = outcome.status
                entity.reason = describe(result.extraction.reason, outcome.notes)
                entity.status
            }!!
        } catch (e: ResponseStatusException) {
            recordFailure(message.id, result.raw, "Activity creation failed: ${e.reason}")
        } catch (e: DateTimeException) {
            recordFailure(message.id, result.raw, "Activity creation failed: ${e.message}")
        } catch (e: ArithmeticException) {
            recordFailure(message.id, result.raw, "Activity creation failed: ${e.message}")
        }
    }

    private fun announce(
        entity: SlackDowntimeMessage,
        extraction: DowntimeExtraction,
        catalog: Map<UUID, no.nav.openinghours.model.db.Service>
    ): DowntimeOutcome {
        val mapped = toActivityInputs(extraction, catalog, entity.rawText, entity.postedAt)
        val notes = mapped.notes.toMutableList()
        val fresh = mapped.inputs.mapNotNull { (index, input) -> withoutCoveredServices(input, catalog, "activities[$index]", notes) }
        if (fresh.isEmpty()) return DowntimeOutcome(NOT_RELEVANT, notes)
        val created = activityService.createAll(fresh)
        entity.activityIds.addAll(created.map { it.id })
        log.info("Created {} activit(y/ies) from Slack message id={}", created.size, entity.id)
        return DowntimeOutcome(ACTIVITY_CREATED, notes)
    }

    /**
     * Removes services that already have an overlapping activity, e.g. from an edited or forwarded post, so only
     * the services without one get a new activity. Returns null when every service is already covered.
     */
    private fun withoutCoveredServices(
        input: ActivityInput,
        catalog: Map<UUID, no.nav.openinghours.model.db.Service>,
        label: String,
        notes: MutableList<String>
    ): ActivityInput? {
        val targets = ServiceTargets(input.serviceIds, input.serviceNames, emptyList())
        val existing = activityRepo.findOverlapping(input.start.atZone(clock.zone).toInstant(), input.end.atZone(clock.zone).toInstant())
            .filter { targets.matches(it, catalog) }
        if (existing.isEmpty()) return input
        val coveredIds = existing.flatMap { it.serviceIds }.toSet()
        val coveredKeys = existing.flatMap { ServiceTargets.activityKeys(it, catalog) }.toSet()
        val ids = input.serviceIds.filterTo(linkedSetOf()) { it !in coveredIds && catalog[it]?.name?.let(ServiceTargets::key) !in coveredKeys }
        val names = input.serviceNames.filterTo(linkedSetOf()) { ServiceTargets.key(it) !in coveredKeys }
        val existingIds = existing.joinToString { it.id.toString() }
        if (ids.isEmpty() && names.isEmpty()) {
            notes += "$label: duplicate of activity $existingIds; skipped"
            return null
        }
        val removed = (input.serviceNames - names).ifEmpty { input.serviceIds - ids }
        notes += "$label: ${removed.joinToString()} already covered by activity $existingIds; created for the remaining services"
        return input.copy(serviceIds = ids, serviceNames = names)
    }

    private fun recordFailure(id: UUID, extraction: String?, error: String): SlackDowntimeMessageStatus =
        transactions.execute {
            val entity = repo.findById(id).orElseThrow()
            entity.attempts++
            entity.reason = error.take(2000)
            extraction?.let { entity.extraction = it }
            entity.status = if (entity.attempts >= maxAttempts) FAILED else PENDING
            log.warn("Slack downtime message id={} attempt={} failed: {}", id, entity.attempts, error)
            entity.status
        }!!

    internal data class MappedActivities(val inputs: List<IndexedValue<ActivityInput>>, val notes: List<String>)

    /**
     * Turns model output into validated activity inputs. Services are kept by id when known and by
     * name otherwise; times are resolved by [TimeWindowResolver]. Assumptions and skipped activities
     * are returned as notes. Activities that have already ended are kept, so short ones are recorded.
     */
    internal fun toActivityInputs(
        extraction: DowntimeExtraction,
        catalog: Map<UUID, no.nav.openinghours.model.db.Service>,
        rawText: String,
        postedAt: Instant
    ): MappedActivities {
        val posted = postedAt.atZone(clock.zone).toLocalDateTime()
        val fallbackMessage = SlackText.clean(rawText).ifBlank { rawText.trim() }
        val notes = mutableListOf<String>()
        val inputs = extraction.activities.withIndex().mapNotNull { (index, a) ->
            val label = "activities[$index]"
            fun skip(why: String): IndexedValue<ActivityInput>? { notes += "$label: $why"; return null }
            val targets = ServiceTargets.from(a.services, catalog)
            targets.notes.forEach { notes += "$label: $it" }
            if (targets.isEmpty) return@mapNotNull skip("no services")
            val hints = ExtractionMapping.hints(a)
            hints.notes.forEach { notes += "$label: $it" }
            val window = when (val w = TimeWindowResolver.resolve(hints.hints ?: return@mapNotNull skip(hints.error!!), posted)) {
                is ResolvedWindow.Rejected -> return@mapNotNull skip(w.reason)
                is ResolvedWindow.Window -> w
            }
            window.assumptions.forEach { notes += "$label: $it" }
            IndexedValue(index, ActivityInput(
                serviceIds = targets.ids,
                serviceNames = targets.names,
                header = a.header?.let(SlackText::clean)?.takeIf { it.isNotEmpty() }?.take(ActivityService.HEADER_MAX) ?: DEFAULT_HEADER,
                message = a.message?.let(SlackText::clean)?.takeIf { it.isNotEmpty() } ?: fallbackMessage,
                start = window.start,
                end = window.end,
                onlyShowForNavEmployees = onlyShowForNavEmployees,
                source = ActivitySource.SLACK
            ))
        }.distinctBy { it.value }
        return MappedActivities(inputs, notes)
    }

    private fun describe(reason: String?, skipped: List<String>): String? =
        (listOfNotNull(reason?.trim()?.takeIf { it.isNotEmpty() }) + skipped).joinToString("; ").takeIf { it.isNotEmpty() }

    private fun toView(m: SlackDowntimeMessage) = SlackDowntimeMessageView(
        id = m.id,
        channelId = m.channelId,
        messageTs = m.messageTs,
        threadTs = m.threadTs,
        postedAt = m.postedAt.atZone(clock.zone).toOffsetDateTime(),
        rawText = m.rawText,
        status = m.status,
        extraction = m.extraction,
        reason = m.reason,
        attempts = m.attempts,
        activityIds = m.activityIds.sorted()
    )
}
