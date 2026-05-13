package no.nav.openinghours.evaluator

import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth

@Component
class OpeningHoursEvaluator {

    private sealed interface EvalResult {
        data object NotApplicable : EvalResult
        data class Matched(val openingHours: String, val ruleName: String, val rule: String) : EvalResult
    }

    fun getOpeningHours(date: LocalDate, group: ResolvedGroup): String =
        when (val r = evaluate(date, group.entries, isSubGroup = false)) {
            is EvalResult.Matched -> r.openingHours
            EvalResult.NotApplicable -> "00:00-00:00"
        }

    fun getDisplayData(date: LocalDate, group: ResolvedGroup): OpeningHoursDisplayData =
        when (val r = evaluate(date, group.entries, isSubGroup = false)) {
            is EvalResult.Matched -> OpeningHoursDisplayData(
                ruleName = r.ruleName,
                rule = r.rule,
                openingHours = r.openingHours,
                displayText = r.openingHours,
            )
            EvalResult.NotApplicable -> OpeningHoursDisplayData(
                rule = "No Rules stated",
                openingHours = "00:00-00:00",
                displayText = "Åpen - ingen gjeldende dato regler",
            )
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

    private fun evaluate(date: LocalDate, entries: List<ResolvedEntry>, isSubGroup: Boolean): EvalResult {
        for (entry in entries) {
            val result = when (entry) {
                is ResolvedRule -> evaluateRule(date, entry)
                is ResolvedGroup -> evaluate(date, entry.entries, isSubGroup = true)
            }
            if (result is EvalResult.Matched) return result
        }
        return EvalResult.NotApplicable
    }

    private fun evaluateRule(date: LocalDate, rule: ResolvedRule): EvalResult {
        val parts = rule.rule.split(Regex("\\s+"))
        if (parts.size != 4) return EvalResult.NotApplicable
        if (!matchesDate(date, parts[0])) return EvalResult.NotApplicable
        if (!matchesDayOfMonth(date, parts[1])) return EvalResult.NotApplicable
        if (!matchesWeekday(date, parts[2])) return EvalResult.NotApplicable
        return EvalResult.Matched(parts[3], rule.name, rule.rule)
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
        return !time.isBefore(opening) && !time.isAfter(closing)
    }
}