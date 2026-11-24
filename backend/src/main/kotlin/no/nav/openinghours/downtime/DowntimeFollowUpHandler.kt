package no.nav.openinghours.downtime

import jakarta.persistence.EntityManager
import no.nav.openinghours.model.db.Activity
import no.nav.openinghours.model.db.ActivityRepository
import no.nav.openinghours.model.db.ActivitySource
import no.nav.openinghours.model.db.Service
import no.nav.openinghours.model.db.SlackDowntimeMessage
import no.nav.openinghours.model.db.SlackDowntimeMessageRepository
import no.nav.openinghours.model.db.SlackDowntimeMessageStatus
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.UUID

data class DowntimeOutcome(val status: SlackDowntimeMessageStatus, val notes: List<String>)

/**
 * Applies follow-up messages to activities created from earlier Slack messages:
 *
 * - COMPLETION sets the end of the matching activity to the stated finish time, or the message time.
 * - CANCELLATION deletes an activity that has not started, ends one that has, or removes just the
 *   named services when only some of them are cancelled.
 * - POSTPONEMENT moves the activity to the new window, or deletes it when no new time is given.
 *
 * A thread reply targets the activities of its parent message. Other follow-ups target Slack
 * activities announced within [windowHours] before the follow-up that share a service with it; the
 * most recently started one wins. Activities created or edited by hand are never touched.
 * Must run inside a transaction.
 */
@Component
class DowntimeFollowUpHandler(
    private val activityRepo: ActivityRepository,
    private val messageRepo: SlackDowntimeMessageRepository,
    private val entityManager: EntityManager,
    private val clock: Clock,
    @Value("\${slack.downtime.follow-up-window-hours:24}") windowHours: Long
) {
    private val log = LoggerFactory.getLogger(DowntimeFollowUpHandler::class.java)
    private val window = Duration.ofHours(windowHours)
    private val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    fun apply(
        kind: MessageKind,
        message: SlackDowntimeMessage,
        parent: SlackDowntimeMessage?,
        extraction: DowntimeExtraction,
        catalog: Map<UUID, Service>
    ): DowntimeOutcome {
        val run = Run(kind, message, catalog)
        val threadCandidates = parent?.activityIds?.takeIf { it.isNotEmpty() }
            ?.let(::lockSlackActivities)?.takeIf { it.isNotEmpty() }
        val candidates = threadCandidates
            ?: lockSlackActivities(
                activityRepo.findSlackActivitiesAnnouncedBetween(message.postedAt.minus(window), message.postedAt).map { it.id }
            )

        extraction.activities.ifEmpty { listOf(ExtractedActivity()) }.forEachIndexed { index, entry ->
            val label = "activities[$index]"
            val targets = ServiceTargets.from(entry.services, catalog)
            targets.notes.forEach { run.notes += "$label: $it" }
            if (targets.isEmpty && threadCandidates == null) {
                run.notes += "$label: no services stated and no thread parent with activities"
                return@forEachIndexed
            }
            val matches = candidates.filter { it.id !in run.deleted && (targets.isEmpty || targets.matches(it, catalog)) }
            when (kind) {
                MessageKind.COMPLETION -> run.complete(label, entry, matches)
                MessageKind.CANCELLATION -> run.cancel(label, entry, targets, matches)
                MessageKind.POSTPONEMENT -> run.postpone(label, entry, matches)
                else -> error("Not a follow-up: $kind")
            }
        }

        val updated = run.updated.filterKeys { it !in run.deleted }
        message.activityIds.addAll(updated.keys)
        val status = when {
            updated.isNotEmpty() -> SlackDowntimeMessageStatus.ACTIVITY_UPDATED
            run.deleted.isNotEmpty() -> SlackDowntimeMessageStatus.ACTIVITY_CANCELLED
            else -> SlackDowntimeMessageStatus.NOT_RELEVANT
        }
        log.info("Applied {} follow-up from Slack message id={} status={}", kind, message.id, status)
        return DowntimeOutcome(status, run.notes)
    }

    private fun lockSlackActivities(ids: Collection<UUID>): List<Activity> {
        if (ids.isEmpty()) return emptyList()
        return activityRepo.findAllByIdForUpdate(ids)
            .onEach { entityManager.refresh(it) }
            .filter { it.source == ActivitySource.SLACK }
    }

    private inner class Run(val kind: MessageKind, val message: SlackDowntimeMessage, val catalog: Map<UUID, Service>) {
        val notes = mutableListOf<String>()
        val updated = linkedMapOf<UUID, Activity>()
        val deleted = linkedSetOf<UUID>()
        val posted: LocalDateTime = local(message.postedAt).truncatedTo(ChronoUnit.MINUTES)

        fun complete(label: String, entry: ExtractedActivity, matches: List<Activity>) {
            val at = statedTime(label, entry) ?: return
            val activity = matches.filter { !it.startAt.isAfter(at) }.maxByOrNull { it.startAt }
                ?: return noMatch(label)
            if (!at.isAfter(activity.startAt)) {
                notes += "$label: finish time is not after the start of activity ${activity.id}; ignored"
                return
            }
            setEnd(label, activity, at, "completed")
        }

        fun cancel(label: String, entry: ExtractedActivity, targets: ServiceTargets, matches: List<Activity>) {
            val at = statedTime(label, entry) ?: return
            val activity = matches.maxByOrNull { it.startAt } ?: return noMatch(label)
            when {
                !targets.isEmpty && !targets.coversAll(activity, catalog) -> removeServices(label, activity, targets)
                !activity.startAt.isBefore(at) -> delete(label, activity, "cancelled before start")
                activity.endAt.isAfter(at) -> setEnd(label, activity, at, "stopped")
                else -> notes += "$label: activity ${activity.id} had already ended; unchanged"
            }
        }

        fun postpone(label: String, entry: ExtractedActivity, matches: List<Activity>) {
            val activity = matches.maxByOrNull { it.startAt } ?: return noMatch(label)
            val mapped = ExtractionMapping.hints(entry)
            mapped.notes.forEach { notes += "$label: $it" }
            val hints = mapped.hints ?: run {
                notes += "$label: ${mapped.error}"
                return
            }
            if (!hints.hasTimeInfo) return delete(label, activity, "postponed without a new time")
            when (val w = TimeWindowResolver.resolve(hints, posted)) {
                is ResolvedWindow.Rejected -> notes += "$label: new window rejected: ${w.reason}"
                is ResolvedWindow.Window -> {
                    val before = describe(activity)
                    activity.startAt = instant(w.start)
                    activity.endAt = instant(w.end)
                    updated[activity.id] = activity
                    notes += "$label: activity ${activity.id} moved from $before to ${describe(activity)}" +
                        w.assumptions.joinToString("") { " ($it)" }
                }
            }
        }

        /** The stated time for a completion or cancellation, or the message time when none is stated. */
        private fun statedTime(label: String, entry: ExtractedActivity): Instant? {
            val text = entry.end?.trim()?.takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }
                ?: return instant(posted)
            return runCatching { instant(LocalDateTime.parse(text)) }.getOrElse {
                notes += "$label: invalid end '$text'"
                null
            }
        }

        private fun noMatch(label: String) {
            notes += "$label: ${kind.name.lowercase()} without matching activity"
        }

        private fun setEnd(label: String, activity: Activity, at: Instant, why: String) {
            val before = fmt.format(local(activity.endAt))
            activity.endAt = at
            updated[activity.id] = activity
            notes += "$label: activity ${activity.id} $why; end changed from $before to ${fmt.format(local(at))}"
        }

        private fun removeServices(label: String, activity: Activity, targets: ServiceTargets) {
            val keys = targets.keys(catalog)
            activity.serviceIds.removeIf { it in targets.ids || catalog[it]?.name?.let(ServiceTargets::key) in keys }
            activity.serviceNames.removeIf { ServiceTargets.key(it) in keys }
            if (activity.serviceIds.isEmpty() && activity.serviceNames.isEmpty()) {
                delete(label, activity, "cancelled for all its services")
            } else {
                updated[activity.id] = activity
                notes += "$label: removed ${targets.names.joinToString(", ")} from activity ${activity.id}"
            }
        }

        private fun delete(label: String, activity: Activity, why: String) {
            val summary = "activity ${activity.id} (${describe(activity)}) deleted: $why"
            messageRepo.findLinkedTo(activity.id).filter { it.id != message.id }.forEach { linked ->
                linked.activityIds.remove(activity.id)
                linked.reason = listOfNotNull(linked.reason, "$summary by message ${message.id}").joinToString("; ")
            }
            activityRepo.delete(activity)
            deleted += activity.id
            notes += "$label: $summary"
        }

        private fun describe(a: Activity) = "${fmt.format(local(a.startAt))}–${fmt.format(local(a.endAt))}"
    }

    private fun local(instant: Instant): LocalDateTime = instant.atZone(clock.zone).toLocalDateTime()

    private fun instant(local: LocalDateTime): Instant = local.atZone(clock.zone).toInstant()
}
