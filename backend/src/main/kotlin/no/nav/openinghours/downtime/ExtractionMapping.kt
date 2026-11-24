package no.nav.openinghours.downtime

import no.nav.openinghours.model.db.Activity
import no.nav.openinghours.model.db.Service
import no.nav.openinghours.service.ActivityService
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeParseException
import java.util.UUID

/**
 * Affected services from one extracted activity. [names] holds a name for every service, also for
 * those with an id, so the activity can always be shown with a readable service name.
 */
internal data class ServiceTargets(val ids: Set<UUID>, val names: Set<String>, val notes: List<String>) {
    val isEmpty: Boolean get() = ids.isEmpty() && names.isEmpty()

    /** Lower-case names of the targeted services, used to match activities across ids and names. */
    fun keys(catalog: Map<UUID, Service>): Set<String> =
        (names + ids.mapNotNull { catalog[it]?.name }).map(::key).toSet()

    fun matches(activity: Activity, catalog: Map<UUID, Service>): Boolean =
        activity.serviceIds.any { it in ids } || activityKeys(activity, catalog).any { it in keys(catalog) }

    /** True when every service of [activity] is targeted, i.e. the follow-up concerns the whole activity. */
    fun coversAll(activity: Activity, catalog: Map<UUID, Service>): Boolean {
        val targeted = keys(catalog)
        return activity.serviceIds.all { it in ids || catalog[it]?.name?.let(::key) in targeted } &&
            activity.serviceNames.all { key(it) in targeted }
    }

    companion object {
        fun from(services: List<ExtractedService>, catalog: Map<UUID, Service>): ServiceTargets {
            val ids = linkedSetOf<UUID>()
            val names = mutableListOf<String>()
            val notes = mutableListOf<String>()
            services.forEach { s ->
                val rawId = s.serviceId?.trim()?.takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }
                val known = rawId?.let { runCatching { UUID.fromString(it) }.getOrNull() }?.let(catalog::get)
                if (rawId != null && known == null) notes += "unknown serviceId '$rawId' ignored"
                known?.let { ids += it.id }
                (known?.name ?: s.name?.trim()?.takeIf { it.isNotEmpty() })?.let { names += it.take(ActivityService.SERVICE_NAME_MAX) }
            }
            return ServiceTargets(ids, ActivityService.normalizeNames(names), notes)
        }

        fun key(name: String): String = name.trim().replace(Regex("\\s+"), " ").lowercase()

        fun activityKeys(activity: Activity, catalog: Map<UUID, Service>): Set<String> =
            (activity.serviceNames + activity.serviceIds.mapNotNull { catalog[it]?.name }).map(::key).toSet()
    }
}

internal data class MappedHints(val hints: TimeHints?, val error: String?, val notes: List<String>)

internal object ExtractionMapping {

    fun hints(a: ExtractedActivity): MappedHints {
        val notes = mutableListOf<String>()
        val start = parseDateTime(a.start) ?: return MappedHints(null, "invalid start '${a.start}'", notes)
        val end = parseDateTime(a.end) ?: return MappedHints(null, "invalid end '${a.end}'", notes)
        val date = parseDate(a.date) ?: return MappedHints(null, "invalid date '${a.date}'", notes)
        val duration = a.duration?.let { d ->
            val unit = DurationUnit.entries.firstOrNull { it.name.equals(d.unit?.trim(), ignoreCase = true) }
            if (d.amount == null || unit == null) {
                notes += "ignored unreadable duration ${d.amount} ${d.unit}"
                null
            } else DurationHint(d.amount, unit)
        }
        return MappedHints(TimeHints(start.value, end.value, date.value, duration, a.short), null, notes)
    }

    /** Wraps a parsed optional value so that "absent" (null value) differs from "unparseable" (null wrapper). */
    data class Parsed<T>(val value: T?)

    private fun parseDateTime(value: String?): Parsed<LocalDateTime>? = parse(value, LocalDateTime::parse)

    private fun parseDate(value: String?): Parsed<LocalDate>? = parse(value, LocalDate::parse)

    private fun <T> parse(value: String?, parser: (String) -> T): Parsed<T>? {
        val text = value?.trim()?.takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) } ?: return Parsed(null)
        return try {
            Parsed(parser(text))
        } catch (_: DateTimeParseException) {
            null
        }
    }
}
