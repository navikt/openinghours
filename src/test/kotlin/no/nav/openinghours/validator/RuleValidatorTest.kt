package no.nav.openinghours.validator

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class RuleValidatorTest {

    private val validator = RuleValidator()

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun valid(rule: String) = assertTrue(validator.isAValidRule(rule), "Expected valid: $rule")
    private fun invalid(rule: String) = assertFalse(validator.isAValidRule(rule), "Expected invalid: $rule")

    // -------------------------------------------------------------------------
    // Part 1 — Date (DD.MM.YYYY)
    // -------------------------------------------------------------------------

    @Nested
    inner class DatePart {

        @Test
        fun `full wildcard is valid`() {
            valid("??.??.???? ? ? 07:00-21:00")
        }

        @Test
        fun `month-only wildcard is valid`() {
            valid("??.04.???? ? ? 10:00-16:00")
        }

        @Test
        fun `day-and-month wildcard, specific year is valid`() {
            valid("24.12.???? ? 1-5 09:00-14:00")
        }

        @Test
        fun `fully specific date is valid`() {
            valid("01.05.2023 ? ? 00:00-23:59")
        }

        @Test
        fun `invalid month 13 is rejected`() {
            invalid("01.13.2023 ? ? 07:00-21:00")
        }

        @Test
        fun `invalid day 32 is rejected`() {
            invalid("32.01.2023 ? ? 07:00-21:00")
        }

        @Test
        fun `Feb 30 is rejected`() {
            invalid("30.02.2023 ? ? 07:00-21:00")
        }

        @Test
        fun `Feb 29 on non-leap year is rejected`() {
            invalid("29.02.2023 ? ? 07:00-21:00")
        }

        @Test
        fun `Feb 29 on leap year is valid`() {
            valid("29.02.2024 ? ? 07:00-21:00")
        }

        @Test
        fun `April 31 is rejected`() {
            invalid("31.04.2023 ? ? 07:00-21:00")
        }

        @Test
        fun `missing date part is rejected`() {
            invalid("01.05 ? ? 07:00-21:00")
        }
    }

    // -------------------------------------------------------------------------
    // Part 2 — Day in Month
    // -------------------------------------------------------------------------

    @Nested
    inner class DayInMonthPart {

        @Test
        fun `wildcard is valid`() {
            valid("??.??.???? ? ? 07:00-21:00")
        }

        @Test
        fun `L (last day) is valid`() {
            valid("??.??.???? L ? 07:00-18:00")
        }

        @Test
        fun `exact day is valid`() {
            valid("??.??.???? 6 1-2 12:00-18:30")
        }

        @Test
        fun `simple range is valid`() {
            valid("??.??.???? 1-5 ? 07:00-21:00")
        }

        @Test
        fun `multi-segment range is valid`() {
            valid("??.??.???? 1-5,25-30 ? 07:00-21:00")
        }

        @Test
        fun `range ending with L is valid`() {
            valid("??.??.???? 1-5,10-L ? 07:00-21:00")
        }

        @Test
        fun `L in middle of expression is rejected`() {
            invalid("??.??.???? L-5 ? 07:00-21:00")
        }

        @Test
        fun `descending range is rejected`() {
            invalid("??.??.???? 10-5 ? 07:00-21:00")
        }

        @Test
        fun `day 32 is rejected`() {
            invalid("??.??.???? 32 ? 07:00-21:00")
        }
    }

    // -------------------------------------------------------------------------
    // Part 3 — Weekday (ISO 8601: 1=Mon … 7=Sun)
    // -------------------------------------------------------------------------

    @Nested
    inner class WeekdayPart {

        @Test
        fun `wildcard is valid`() {
            valid("17.05.???? ? ? 00:00-00:00")
        }

        @Test
        fun `Monday to Friday is valid`() {
            valid("??.??.???? ? 1-5 07:00-21:00")
        }

        @Test
        fun `weekend is valid`() {
            valid("??.??.???? ? 6-7 07:00-21:00")
        }

        @Test
        fun `single weekday is valid`() {
            valid("??.??.???? ? 3 07:00-21:00")
        }

        @Test
        fun `weekday 0 is rejected`() {
            invalid("??.??.???? ? 0-5 07:00-21:00")
        }

        @Test
        fun `weekday 8 is rejected`() {
            invalid("??.??.???? ? 1-8 07:00-21:00")
        }

        @Test
        fun `descending weekday range is rejected`() {
            invalid("??.??.???? ? 5-1 07:00-21:00")
        }
    }

    // -------------------------------------------------------------------------
    // Part 4 — Opening Hours (HH:MM-HH:MM)
    // -------------------------------------------------------------------------

    @Nested
    inner class TimePart {

        @Test
        fun `standard open hours are valid`() {
            valid("??.??.???? ? 1-5 07:00-21:00")
        }

        @Test
        fun `closed sentinel 00-00 is valid`() {
            valid("17.05.???? ? ? 00:00-00:00")
        }

        @Test
        fun `all-day open 00-00 to 23-59 is valid`() {
            valid("01.05.2023 ? ? 00:00-23:59")
        }

        @Test
        fun `closing before opening is rejected`() {
            invalid("??.??.???? ? 1-5 21:00-07:00")
        }

        @Test
        fun `invalid hour 25 is rejected`() {
            invalid("??.??.???? ? ? 25:00-26:00")
        }

        @Test
        fun `invalid minute 60 is rejected`() {
            invalid("??.??.???? ? ? 07:60-21:00")
        }

        @Test
        fun `missing time separator is rejected`() {
            invalid("??.??.???? ? ? 0700-2100")
        }
    }

    // -------------------------------------------------------------------------
    // Structural / whole-rule checks
    // -------------------------------------------------------------------------

    @Nested
    inner class WholeRule {

        @Test
        fun `too few parts is rejected`() {
            invalid("??.??.???? ? 07:00-21:00")
        }

        @Test
        fun `too many parts is rejected`() {
            invalid("??.??.???? ? ? 07:00-21:00 extra")
        }

        @Test
        fun `empty string is rejected`() {
            invalid("")
        }

        @Test
        fun `all example rules from DSL spec are valid`() {
            val examples = listOf(
                "??.??.???? ? 1-5 07:00-21:00",
                "24.12.???? ? 1-5 09:00-14:00",
                "17.05.???? ? ? 00:00-00:00",
                "??.??.???? L ? 07:00-18:00",
                "??.04.???? ? 1-5 10:00-16:00",
                "01.05.2023 ? ? 00:00-23:59"
            )
            examples.forEach { valid(it) }
        }
    }
}
