package no.nav.openinghours.validator

import org.springframework.stereotype.Component
import java.text.ParseException
import java.text.SimpleDateFormat

@Component
class OpeningHoursValidator {

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
            val ddmmRule = dateRule.substring(0, 5)
            return ddmmRule.matches(Regex("^(0?[1-9]|[12][0-9]|3[01])\\.(0?[1-9]|1[0-2])$"))
        }

        return dateRule.matches(Regex("(((0[1-9]|[12][0-9]|3[01])\\.((0[13578]|10|12))\\.(\\d{4}))" +
                "|((0[1-9]|[12][0-9]|30)\\.(0[469]|11)\\.(\\d{4}))" +
                "|((0[1-9]|1[0-9]|2[0-8])\\.(02)\\.(\\d{4}))" +
                "|((29)\\.(02)\\.(\\d{4}))" +
                ")"))
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
        for (i in 1 until ruleParts.size) {
            val upperRange = ruleParts[i].toIntOrNull() ?: return false
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
        for (i in 1 until ruleParts.size) {
            val upperRange = ruleParts[i].toIntOrNull() ?: return false
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