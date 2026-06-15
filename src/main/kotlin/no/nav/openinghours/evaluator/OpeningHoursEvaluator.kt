package no.nav.openinghours.evaluator

import org.springframework.stereotype.Component
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth

@Component
class OpeningHoursEvaluator {

    companion object {
        /** Returned whenever a group has no rules. Callers may also use this as a fallback when no rule matches. */
        val DEFAULT_DISPLAY_DATA = OpeningHoursDisplayData(
            ruleName = "No Rules stated",
            rule = "??.??.???? ? ? 00:00-23:59",
            openingHours = "00:00-23:59",
            displayHeader = "Default regel",
            displayText = "Åpent - ingen gjeldende dato regler",
            onlyShowForNavEmployees = false,
        )

        /**
         * Determines whether a service is currently open based on its opening hours string and the given time.
         *
         * - `"00:00-23:59"` → always open (`true`)
         * - `"00:00-00:00"` → always closed (`false`)
         * - Malformed string (bad format or unparseable time component) → `false`
         * - Normal range (open ≤ close): open if [now] is within `[openTime, closeTime]` (inclusive, no tolerance)
         * - Cross-midnight range (open > close, e.g. `"22:00-02:00"`): open if [now] is ≥ openTime
         *   **or** ≤ closeTime — mirrors the logic in [matchesTime] but without the ±1-minute DSL tolerance
         */
        fun computeIsOpen(hours: String, now: LocalTime): Boolean {
            if (hours == "00:00-23:59") return true
            if (hours == "00:00-00:00") return false
            val parts = hours.split("-")
            if (parts.size != 2) return false
            val openTime = runCatching { LocalTime.parse(parts[0]) }.getOrNull() ?: return false
            val closeTime = runCatching { LocalTime.parse(parts[1]) }.getOrNull() ?: return false
            return if (openTime <= closeTime) {
                !now.isBefore(openTime) && !now.isAfter(closeTime)
            } else {
                // cross-midnight: open from openTime until closeTime the next day
                !now.isBefore(openTime) || !now.isAfter(closeTime)
            }
        }

        /**
         * Determines the `isOpen` value for a given [date] and [hours] string, using pre-captured
         * [today] and [nowTime] values derived from a single clock snapshot.
         *
         * Two distinct semantics are applied depending on whether [date] is today:
         * - **Today** (`date == today`): "open right now" — delegates to [computeIsOpen] with [nowTime].
         * - **Any other date** (past or future): "open at all" — returns `true` unless the hours are the
         *   sentinel "00:00-00:00" (always-closed), preserving the original contract for range/future
         *   queries where a real-time check would be meaningless.
         *
         * Prefer this overload over [computeIsOpenOnDate(hours, date, clock)] whenever you also need
         * the [today] value elsewhere (e.g. to derive fallback display times), so that both decisions
         * are based on the same instant and cannot disagree across a midnight boundary.
         */
        fun computeIsOpenOnDate(hours: String, date: LocalDate, today: LocalDate, nowTime: LocalTime): Boolean =
            if (date == today) computeIsOpen(hours, nowTime)
            else hours != "00:00-00:00"

        /**
         * Convenience overload that snapshots [clock] once into a [LocalDateTime] and delegates to
         * [computeIsOpenOnDate(hours, date, today, nowTime)]. Callers that also need the current
         * date/time for other purposes should capture `LocalDateTime.now(clock)` themselves and call
         * the primary overload directly to avoid separate clock reads.
         */
        fun computeIsOpenOnDate(hours: String, date: LocalDate, clock: Clock): Boolean {
            val now = LocalDateTime.now(clock)
            return computeIsOpenOnDate(hours, date, now.toLocalDate(), now.toLocalTime())
        }
    }

    private sealed interface EvalResult {
        data object NoRules : EvalResult
        data object NoMatch : EvalResult
        data class Matched(val openingHours: String, val ruleName: String, val rule: String, val displayHeader: String? = null, val displayText: String? = null, val onlyShowForNavEmployees: Boolean = false, val redDay: Boolean = false) : EvalResult
    }

    fun getOpeningHours(date: LocalDate, group: ResolvedGroup): String =
        when (val r = evaluate(date, group.entries)) {
            is EvalResult.Matched -> r.openingHours
            EvalResult.NoRules, EvalResult.NoMatch -> "00:00-23:59"
        }

    fun getDisplayData(date: LocalDate, group: ResolvedGroup): OpeningHoursDisplayData? =
        when (val r = evaluate(date, group.entries)) {
            is EvalResult.Matched -> OpeningHoursDisplayData(
                ruleName = r.ruleName,
                rule = r.rule,
                openingHours = r.openingHours,
                displayHeader = r.displayHeader,
                displayText = r.displayText,
                onlyShowForNavEmployees = r.onlyShowForNavEmployees,
                redDay = r.redDay,
            )
            EvalResult.NoRules -> DEFAULT_DISPLAY_DATA
            EvalResult.NoMatch -> null
        }

    fun isOpen(dateTime: LocalDateTime, ruleDsl: String): Boolean {
        val parts = ruleDsl.split(Regex("\\s+"))
        if (parts.size != 4) return false
        return matchesDate(dateTime.toLocalDate(), parts[0]) &&
                matchesDayOfMonth(dateTime.toLocalDate(), parts[1]) &&
                matchesWeekday(dateTime.toLocalDate(), parts[2]) &&
                matchesTime(dateTime.toLocalTime(), parts[3])
    }

    fun getOpeningTime(timeRule: String): LocalTime {
        val (h, m) = timeRule.split("-")[0].split(":").map { it.toInt() }
        return LocalTime.of(h, m)
    }

    fun getClosingTime(timeRule: String): LocalTime {
        val (h, m) = timeRule.split("-")[1].split(":").map { it.toInt() }
        // 23:59 rolls to 00:00 next day for uptime calculations
        return if (h == 23 && m == 59) LocalTime.MIDNIGHT else LocalTime.of(h, m)
    }

    private fun evaluate(date: LocalDate, entries: List<ResolvedEntry>): EvalResult {
        if (entries.isEmpty()) return EvalResult.NoRules
        // Track whether any rule was encountered anywhere in the tree.
        // Without this, a group containing only empty sub-groups would return NoMatch
        // instead of NoRules, incorrectly triggering a 404 in getDisplayData().
        var anyRulesFound = false
        for (entry in entries) {
            val result = when (entry) {
                is ResolvedRule -> evaluateRule(date, entry).also { anyRulesFound = true }
                is ResolvedGroup -> evaluate(date, entry.entries)
            }
            when (result) {
                is EvalResult.Matched -> return result
                is EvalResult.NoMatch -> anyRulesFound = true   // sub-group had rules, none matched
                EvalResult.NoRules -> { /* empty sub-group — no rules to count */ }
            }
        }
        return if (anyRulesFound) EvalResult.NoMatch else EvalResult.NoRules
    }

    private fun evaluateRule(date: LocalDate, rule: ResolvedRule): EvalResult {
        val parts = rule.rule.split(Regex("\\s+"))
        if (parts.size != 4) throw IllegalStateException(
            "Malformed rule DSL for rule '${rule.name}': expected 4 parts but got ${parts.size} in \"${rule.rule}\""
        )
        if (!matchesDate(date, parts[0])) return EvalResult.NoMatch
        if (!matchesDayOfMonth(date, parts[1])) return EvalResult.NoMatch
        if (!matchesWeekday(date, parts[2])) return EvalResult.NoMatch
        return EvalResult.Matched(parts[3], rule.name, rule.rule, rule.displayHeader, rule.displayText, rule.onlyShowForNavEmployees, rule.redDay)
    }

    private fun matchesDate(date: LocalDate, datePart: String): Boolean {
        if (datePart == "??.??.????") return true
        val p = datePart.split(".")
        if (p.size != 3) return false
        if (p[2] == "????") {
            if (p[0] == "??") return p[1].toIntOrNull() == date.monthValue
            val dd = p[0].toIntOrNull() ?: return false
            val mm = p[1].toIntOrNull() ?: return false
            return dd == date.dayOfMonth && mm == date.monthValue
        }
        val y = p[2].toIntOrNull() ?: return false
        val m = p[1].toIntOrNull() ?: return false
        val d = p[0].toIntOrNull() ?: return false
        return runCatching { LocalDate.of(y, m, d) }.getOrNull() == date
    }

    private fun matchesDayOfMonth(date: LocalDate, part: String): Boolean {
        if (part == "?") return true
        val lastDay = YearMonth.from(date).lengthOfMonth()
        val expanded = part.replace("L", lastDay.toString())
        val day = date.dayOfMonth
        return expanded.split(",").any { token -> matchesNumberToken(token, day) }
    }

    private fun matchesWeekday(date: LocalDate, part: String): Boolean {
        if (part == "?") return true
        val dow = date.dayOfWeek.value // 1=Mon..7=Sun
        return part.split(",").any { token -> matchesNumberToken(token, dow) }
    }

    private fun matchesNumberToken(token: String, value: Int): Boolean {
        if (token.contains("-")) {
            val (lo, hi) = token.split("-").map { it.toInt() }
            return value in lo..hi
        }
        return token.toIntOrNull() == value
    }

    private fun matchesTime(time: LocalTime, part: String): Boolean {
        if (part == "00:00-00:00") return false
        if (part == "00:00-23:59") return true
        val (openStr, closeStr) = part.split("-")
        val (oh, om) = openStr.split(":").map { it.toInt() }
        val (ch, cm) = closeStr.split(":").map { it.toInt() }
        val opening = LocalTime.of(oh, om).minusMinutes(1)
        val closing = LocalTime.of(ch, cm).plusMinutes(1)
        if (opening <= closing) return !time.isBefore(opening) && !time.isAfter(closing)
        return !time.isBefore(opening) || !time.isAfter(closing)
    }
}
