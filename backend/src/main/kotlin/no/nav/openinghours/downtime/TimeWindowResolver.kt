package no.nav.openinghours.downtime

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.roundToLong

enum class DurationUnit { SECONDS, MINUTES, HOURS, DAYS }

/** A stated duration. The model has already normalised wording: "a couple" = 2, "a few" = 3, ranges = the maximum. */
data class DurationHint(val amount: Double, val unit: DurationUnit)

/** Time information stated in a message, as local date-times in the configured timezone. */
data class TimeHints(
    val start: LocalDateTime? = null,
    val end: LocalDateTime? = null,
    /** A date without any clock time ("lørdag 21. november"). */
    val date: LocalDate? = null,
    val duration: DurationHint? = null,
    /** Vague short downtime without a duration ("kort nedetid", "kortvarig", "restart"). */
    val short: Boolean = false
) {
    val hasTimeInfo: Boolean get() = start != null || end != null || date != null || duration != null
}

sealed interface ResolvedWindow {
    data class Window(val start: LocalDateTime, val end: LocalDateTime, val assumptions: List<String>) : ResolvedWindow
    data class Rejected(val reason: String) : ResolvedWindow
}

/**
 * Turns stated time information into a concrete [start, end) window:
 *
 * | Stated                 | Window                                                                 |
 * |------------------------|------------------------------------------------------------------------|
 * | start + end            | as stated; an end at or before the start on the same date moves to the next day |
 * | start + duration       | end = start + duration                                                 |
 * | end + duration         | start = end − duration                                                 |
 * | end only               | start = message time; an end at or before that on the same date moves to the next day |
 * | start only             | end = start + 30 min if [TimeHints.short], otherwise midnight after the start |
 * | duration only          | start = message time                                                   |
 * | date only (not today)  | the whole day                                                          |
 * | nothing                | start = message time, end as for start only                            |
 *
 * Durations in seconds give a one-minute window. Windows that do not end after they start, or
 * that are longer than [MAX_WINDOW], are rejected.
 */
object TimeWindowResolver {
    val SHORT_DOWNTIME: Duration = Duration.ofMinutes(30)
    val SECONDS_DOWNTIME: Duration = Duration.ofMinutes(1)
    val MAX_WINDOW: Duration = Duration.ofDays(14)

    fun resolve(hints: TimeHints, postedAt: LocalDateTime): ResolvedWindow {
        val posted = postedAt.truncatedTo(ChronoUnit.MINUTES)
        val notes = mutableListOf<String>()
        val duration = hints.duration?.let { toDuration(it, notes) }
        val start = hints.start
        val end = hints.end
        val wholeDay = hints.date?.takeIf { start == null && end == null && it != posted.toLocalDate() }
        val durationSetsWindow = !(start != null && end != null) && wholeDay == null
        // Checked before any date arithmetic, since a huge duration would overflow LocalDateTime.
        if (duration != null && durationSetsWindow && duration > MAX_WINDOW) {
            return ResolvedWindow.Rejected("duration is longer than ${MAX_WINDOW.toDays()} days")
        }

        val (from, to) = when {
            start != null && end != null -> start to nextDayIfNotAfter(start, end, notes)
            start != null && duration != null -> start to start + duration
            end != null && duration != null -> {
                notes += "start derived from end minus duration"
                end - duration to end
            }
            end != null -> {
                notes += "no start stated; used message time"
                posted to nextDayIfNotAfter(posted, end, notes)
            }
            start != null -> start to openEnd(start, hints.short, notes)
            wholeDay != null -> {
                notes += "date without time; used the whole day"
                wholeDay.atStartOfDay() to wholeDay.plusDays(1).atStartOfDay()
            }
            duration != null -> {
                notes += "no start stated; used message time"
                posted to posted + duration
            }
            else -> {
                notes += "no start stated; used message time"
                posted to openEnd(posted, hints.short, notes)
            }
        }

        if (!to.isAfter(from)) return ResolvedWindow.Rejected("end is not after start")
        if (Duration.between(from, to) > MAX_WINDOW) return ResolvedWindow.Rejected("window is longer than ${MAX_WINDOW.toDays()} days")
        return ResolvedWindow.Window(from, to, notes)
    }

    private fun toDuration(hint: DurationHint, notes: MutableList<String>): Duration? {
        if (hint.amount.isNaN() || hint.amount <= 0) {
            notes += "ignored invalid duration ${hint.amount} ${hint.unit}"
            return null
        }
        return when (hint.unit) {
            DurationUnit.SECONDS -> SECONDS_DOWNTIME.also { notes += "duration in seconds; used 1 minute" }
            DurationUnit.MINUTES -> Duration.ofSeconds((hint.amount * 60).roundToLong())
            DurationUnit.HOURS -> Duration.ofSeconds((hint.amount * 3600).roundToLong())
            DurationUnit.DAYS -> Duration.ofSeconds((hint.amount * 86400).roundToLong())
        }
    }

    private fun nextDayIfNotAfter(start: LocalDateTime, end: LocalDateTime, notes: MutableList<String>): LocalDateTime {
        if (end.isAfter(start) || end.toLocalDate() != start.toLocalDate()) return end
        notes += "end at or before start on the same date; moved end to the next day"
        return end.plusDays(1)
    }

    private fun openEnd(start: LocalDateTime, short: Boolean, notes: MutableList<String>): LocalDateTime =
        if (short) {
            notes += "short downtime without duration; assumed ${SHORT_DOWNTIME.toMinutes()} minutes"
            start + SHORT_DOWNTIME
        } else {
            notes += "no end or duration stated; assumed end of day"
            start.toLocalDate().plusDays(1).atStartOfDay()
        }
}
