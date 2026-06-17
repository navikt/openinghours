package no.nav.openinghours.validator

import org.springframework.stereotype.Component
import java.text.ParseException
import java.text.SimpleDateFormat

@Component
class RuleValidator {

    fun isAValidRule(rule: String): Boolean {
        val ruleParts = rule.trim().split(Regex("\\s+"))
        if (ruleParts.size != 4) return false

        return isValidDateFormat(ruleParts[0]) &&
                isValidDayInMonthFormat(ruleParts[1]) &&
                isValidWeekdayFormat(ruleParts[2]) &&
                isValidTimeFormat(ruleParts[3])
    }

    private fun isValidDateFormat(dateRule: String): Boolean {
        if (dateRule == "??.??.????") return true

        val ruleParts = dateRule.split(".")
        if (ruleParts.size != 3) return false

        if (ruleParts[2] == "????") {
            if (ruleParts[0] == "??") {
                return ruleParts[1].matches(Regex("^(0?[1-9]|1[0-2])$"))
            }
            val ddmmRule = "${ruleParts[0]}.${ruleParts[1]}"
            return ddmmRule.matches(Regex("^(0?[1-9]|[12][0-9]|3[01])\\.(0?[1-9]|1[0-2])$"))
        }

        val regexddmmyyyy = Regex("^\\d{2}\\.\\d{2}\\.\\d{4}$") // Matches dd.MM.yyyy format
        if (!regexddmmyyyy.matches(dateRule)) {
            println("Date does not match the expected format: $dateRule")
            return false
        }

        val regex31Days = Regex("^(0[1-9]|[12][0-9]|3[01])\\.(0[13578]|1[02])\\.(\\d{4})$") // Matches months with 31 days
        val regex30Days = Regex("^(0[1-9]|[12][0-9]|30)\\.(0[469]|11)\\.(\\d{4})$")         // Matches months with 30 days
        val regexFebNonLeap = Regex("^(0[1-9]|1[0-9]|2[0-8])\\.02\\.(\\d{4})$")            // Matches February (non-leap years)
        val regexFebLeap = Regex("^29\\.02\\.(\\d{4})$")                                   // Matches February 29 (leap years)

        return regex31Days.matches(dateRule) ||
                regex30Days.matches(dateRule) ||
                regexFebNonLeap.matches(dateRule) ||
                (regexFebLeap.matches(dateRule) && isLeapYear(dateRule.split(".")[2].toInt()))
    }

    private fun isLeapYear(year: Int): Boolean {
        return (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0)
    }

    private fun isValidDayInMonthFormat(dayInMonthRule: String): Boolean {
        if (dayInMonthRule == "?") return true
        if (dayInMonthRule == "L") return true

        // Split by comma into tokens; each token is either a single value, a range (e.g. "5-12"), or "L"
        val tokens = dayInMonthRule.split(",")
        if (tokens.isEmpty()) return false

        var previousMax = 0 // track ascending order across tokens

        for ((index, token) in tokens.withIndex()) {
            if (token == "L") {
                // L must be the last token
                if (index != tokens.size - 1) return false
                continue
            }

            val rangeParts = token.split("-")
            when (rangeParts.size) {
                1 -> {
                    val value = rangeParts[0].toIntOrNull() ?: return false
                    if (value < 1 || value > 31) return false
                    if (value <= previousMax) return false
                    previousMax = value
                }
                2 -> {
                    // Range: lower-upper, upper can be "L"
                    val lower = rangeParts[0].toIntOrNull() ?: return false
                    if (lower < 1 || lower > 31) return false
                    if (lower <= previousMax) return false

                    if (rangeParts[1] == "L") {
                        // "N-L" must be the last token
                        if (index != tokens.size - 1) return false
                        previousMax = 31 // L represents end-of-month
                    } else {
                        val upper = rangeParts[1].toIntOrNull() ?: return false
                        if (upper < 1 || upper > 31) return false
                        if (lower > upper) return false
                        previousMax = upper
                    }
                }
                else -> return false
            }
        }
        return true
    }

    private fun isValidWeekdayFormat(weekDayRule: String): Boolean {
        if (weekDayRule == "?") return true

        // Parse like the evaluator (comma-separated tokens, each optionally a '-' range),
        // and additionally enforce ascending, non-overlapping tokens (via previousMax).
        // This rejects multi-dash tokens like "1-3-5" and out-of-range values outside [1-7].
        val tokens = weekDayRule.split(",", limit = -1)
        if (tokens.any { it.isBlank() }) return false
        var previousMax = 0

        for (token in tokens) {
            val rangeParts = token.split("-")
            when (rangeParts.size) {
                1 -> {
                    val value = rangeParts[0].toIntOrNull() ?: return false
                    if (value < 1 || value > 7) return false
                    if (value <= previousMax) return false
                    previousMax = value
                }
                2 -> {
                    val lo = rangeParts[0].toIntOrNull() ?: return false
                    val hi = rangeParts[1].toIntOrNull() ?: return false
                    if (lo < 1 || lo > 7) return false
                    if (hi < 1 || hi > 7) return false
                    if (lo > hi) return false
                    if (lo <= previousMax) return false
                    previousMax = hi
                }
                else -> return false // e.g. "1-3-5" — more than one '-' in a token
            }
        }
        return true
    }

    private fun isValidTimeFormat(timeRule: String): Boolean {
        if (!timeRule.matches(Regex("^([0-9]|0[0-9]|1[0-9]|2[0-3]):([0-9]|[0-5][0-9])-([0-9]|0[0-9]|1[0-9]|2[0-3]):([0-9]|[0-5][0-9])$"))) {
            return false
        }

        val ruleParts = timeRule.split("-")
        val sdf = SimpleDateFormat("HH:mm")
        return try {
            val midnight = sdf.parse("00:00")
            val opening = sdf.parse(ruleParts[0])
            val closing = sdf.parse(ruleParts[1])
            !(closing.before(opening) && !(opening == midnight && closing == midnight))
        } catch (e: ParseException) {
            false
        }
    }
}