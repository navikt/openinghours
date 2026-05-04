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

        val ruleParts = dayInMonthRule.split("[,-]".toRegex())
        if (ruleParts.size == 1) {
            return dayInMonthRule.matches(Regex("^([0-2]?[0-9]|3[01]|L)$"))
        }

        val containsL = dayInMonthRule.contains("L")
        val lPosition = dayInMonthRule.indexOf("L")
        if (containsL && lPosition != dayInMonthRule.length - 1) return false

        var lowerRange = ruleParts[0].toIntOrNull() ?: return false
        if (lowerRange < 1 || lowerRange > 31) return false
        for (i in 1 until ruleParts.size) {
            val part = ruleParts[i]
            if (part == "L") break  // L is valid only at end (already checked above)
            val upperRange = part.toIntOrNull() ?: return false
            if (upperRange < 1 || upperRange > 31) return false
            if (lowerRange > upperRange) return false
            lowerRange = upperRange
        }
        return true
    }

    private fun isValidWeekdayFormat(weekDayRule: String): Boolean {
        if (weekDayRule == "?") return true

        val ruleParts = weekDayRule.split("[,-]".toRegex())
        if (ruleParts.size == 1) {
            return weekDayRule.matches(Regex("^[1-7]$"))
        }

        var lowerRange = ruleParts[0].toIntOrNull() ?: return false
        if (lowerRange < 1 || lowerRange > 7) return false
        for (i in 1 until ruleParts.size) {
            val upperRange = ruleParts[i].toIntOrNull() ?: return false
            if (upperRange < 1 || upperRange > 7) return false
            if (lowerRange > upperRange) return false
            lowerRange = upperRange
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