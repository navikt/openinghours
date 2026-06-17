package no.nav.openinghours.validator

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class RuleValidatorTest {

    private val validator = RuleValidator()

    @Test
    fun `validate date format part`() {
        assertThat(validator.isAValidRule("??.??.???? ? ")).isFalse
        assertThat(validator.isAValidRule("02.05.2022 ? 1-5 ?")).isFalse
        assertThat(validator.isAValidRule("02.05.2022 ? ? 09:00-22:00")).isTrue
        assertThat(validator.isAValidRule("11.12.2022 ? ? 09:00-22:00")).isTrue
        assertThat(validator.isAValidRule("51.12.2022 ? ? 09:00-22:00")).isFalse
        assertThat(validator.isAValidRule("01.15.???? ? ? 09:00-22:00")).isFalse
        assertThat(validator.isAValidRule("11.??.2022 ? ? 09:00-22:00")).isFalse
        assertThat(validator.isAValidRule("14.12.20zz ? ? 09:00-22:00")).isFalse
        assertThat(validator.isAValidRule("14.12.9999 ? ? 09:00-22:00")).isTrue
        assertThat(validator.isAValidRule("29.02.2022 ? ? 10:00-22:00")).isFalse
        assertThat(validator.isAValidRule("29.02.2024 ? ? 10:00-22:00")).isTrue
        assertThat(validator.isAValidRule("11/12/2022 ? ? 09:00-22:00")).isFalse
        assertThat(validator.isAValidRule("??.01.???? ? ? 09:00-22:00")).isTrue
        assertThat(validator.isAValidRule("??.13.???? ? ? 09:00-22:00")).isFalse
        assertThat(validator.isAValidRule("12.??.???? ? ? 09:00-22:00")).isFalse
    }

    @Test
    fun `validate day in month format`() {
        assertThat(validator.isAValidRule("??.??.???? 5 ? 07:00-21:00")).isTrue
        assertThat(validator.isAValidRule("??.??.???? L ? 07:00-21:00")).isTrue
        assertThat(validator.isAValidRule("??.??.???? 31 ? 07:00-21:00")).isTrue
        assertThat(validator.isAValidRule("??.??.???? 32 ? 07:00-21:00")).isFalse
        assertThat(validator.isAValidRule("??.??.???? 1,2,L,30 ? 7:00-21:00")).isFalse
        assertThat(validator.isAValidRule("??.??.???? 11,17,19,L ? 07:00-21:00")).isTrue
        assertThat(validator.isAValidRule("??.??.???? 18,17,19,L ? 07:00-21:00")).isFalse
        assertThat(validator.isAValidRule("??.??.???? 1-3,6,11,17,23 ? 07:00-21:00")).isTrue
        assertThat(validator.isAValidRule("??.??.???? 5-12 ? 07:00-21:00")).isTrue
        assertThat(validator.isAValidRule("??.??.???? 18,1-2 ? 07:00-21:00")).isFalse
    }

    @Test
    fun `validate weekday format`() {
        assertThat(validator.isAValidRule("??.??.???? ? 1 07:00-21:00")).isTrue
        assertThat(validator.isAValidRule("??.??.???? ? 1-3 07:00-21:00")).isTrue
        assertThat(validator.isAValidRule("??.??.???? ? 2,4 07:00-21:00")).isTrue
        assertThat(validator.isAValidRule("??.??.???? ? 5,4,3 07:00-21:00")).isFalse
        assertThat(validator.isAValidRule("??.??.???? ? 5-4,1 07:00-21:00")).isFalse
        assertThat(validator.isAValidRule("??.??.???? ? ? 07:00-21:00")).isTrue
    }

    @Test
    fun `validate weekday rejects out-of-range values`() {
        // Upper bound above 7
        assertThat(validator.isAValidRule("??.??.???? ? 1-8 07:00-21:00")).isFalse
        // Lower bound below 1
        assertThat(validator.isAValidRule("??.??.???? ? 0-5 07:00-21:00")).isFalse
        // Comma-separated value above 7
        assertThat(validator.isAValidRule("??.??.???? ? 1,8 07:00-21:00")).isFalse
        // Single value above 7
        assertThat(validator.isAValidRule("??.??.???? ? 8 07:00-21:00")).isFalse
        // Zero as single value
        assertThat(validator.isAValidRule("??.??.???? ? 0 07:00-21:00")).isFalse
        // Both bounds out of range
        assertThat(validator.isAValidRule("??.??.???? ? 0-8 07:00-21:00")).isFalse
        // Exactly 7 is valid (Sunday)
        assertThat(validator.isAValidRule("??.??.???? ? 7 07:00-21:00")).isTrue
        assertThat(validator.isAValidRule("??.??.???? ? 1-7 07:00-21:00")).isTrue
    }

    @Test
    fun `validate weekday rejects multi-dash tokens`() {
        // "1-3-5" would be silently mis-evaluated by the evaluator (treats it as 1-3)
        assertThat(validator.isAValidRule("??.??.???? ? 1-3-5 07:00-21:00")).isFalse
        // "1-2-3" same issue
        assertThat(validator.isAValidRule("??.??.???? ? 1-2-3 07:00-21:00")).isFalse
        // Comma prefix with multi-dash token
        assertThat(validator.isAValidRule("??.??.???? ? 1,3-5-7 07:00-21:00")).isFalse
    }

    @Test
    fun `validate time format`() {
        assertThat(validator.isAValidRule("??.??.???? ? ? 07:00-21:00")).isTrue
        assertThat(validator.isAValidRule("??.??.???? ? ? 00:00-21:00")).isTrue
        assertThat(validator.isAValidRule("??.??.???? ? ? 00:00-00:00")).isTrue
        assertThat(validator.isAValidRule("??.??.???? ? ? 77:29-21:00")).isFalse
        assertThat(validator.isAValidRule("??.??.???? ? ? 12:30-0b:00")).isFalse
        assertThat(validator.isAValidRule("??.??.???? ? ? 16:00-13:00")).isFalse
        assertThat(validator.isAValidRule("??.??.???? ? ? 1a:34-16:18")).isFalse
    }

    @Test
    fun `validate range ending in L for day-in-month`() {
        // "1-5,10-L" — range with L at end; validator splits on commas, so "10-L" is validated as the last token
        assertThat(validator.isAValidRule("??.??.???? 1-5,10-L ? 07:00-21:00")).isTrue
        // Range-like "5-L" — upper bound uses L (last day of month) and must be the last token
        assertThat(validator.isAValidRule("??.??.???? 5-L ? 07:00-21:00")).isTrue
        // L must be at end after comma separation
        assertThat(validator.isAValidRule("??.??.???? 1,5,L ? 07:00-21:00")).isTrue
        assertThat(validator.isAValidRule("??.??.???? 11,17,19,L ? 07:00-21:00")).isTrue
        // L in the middle is invalid (not at last position in string)
        assertThat(validator.isAValidRule("??.??.???? L,5 ? 07:00-21:00")).isFalse
        assertThat(validator.isAValidRule("??.??.???? 1,L,5 ? 07:00-21:00")).isFalse
        // Descending numeric parts before L → invalid
        assertThat(validator.isAValidRule("??.??.???? 10-5,L ? 07:00-21:00")).isFalse
    }

    @Test
    fun `validate L standalone in day-in-month`() {
        // "L" alone means last day of month
        assertThat(validator.isAValidRule("??.??.???? L ? 07:00-18:00")).isTrue
        // L alone but with invalid time
        assertThat(validator.isAValidRule("??.??.???? L ? 25:00-18:00")).isFalse
    }

    @Test
    fun `validate numeric bounds for day-in-month rejects zero`() {
        // 0 as day-of-month — now correctly rejected
        assertThat(validator.isAValidRule("??.??.???? 0 ? 07:00-21:00")).isFalse
        // 0 in multi-value path
        assertThat(validator.isAValidRule("??.??.???? 0,5,10 ? 07:00-21:00")).isFalse
        // 00 is also rejected
        assertThat(validator.isAValidRule("??.??.???? 00 ? 07:00-21:00")).isFalse
        // Negative values don't match
        assertThat(validator.isAValidRule("??.??.???? -1 ? 07:00-21:00")).isFalse
        // 31 is the max valid day
        assertThat(validator.isAValidRule("??.??.???? 31 ? 07:00-21:00")).isTrue
        assertThat(validator.isAValidRule("??.??.???? 32 ? 07:00-21:00")).isFalse
        // Values above 31 in multi-value are also rejected
        assertThat(validator.isAValidRule("??.??.???? 5,45 ? 07:00-21:00")).isFalse
    }

    @Test
    fun `validate time edge case open all day`() {
        // "00:00-23:59" is the open-all-day sentinel
        assertThat(validator.isAValidRule("??.??.???? ? ? 00:00-23:59")).isTrue
        // "23:59-00:00" closing before opening → invalid
        assertThat(validator.isAValidRule("??.??.???? ? ? 23:59-00:00")).isFalse
        // "00:00-00:00" is valid (closed sentinel)
        assertThat(validator.isAValidRule("??.??.???? ? ? 00:00-00:00")).isTrue
        // "23:00-23:59" last valid range
        assertThat(validator.isAValidRule("??.??.???? ? ? 23:00-23:59")).isTrue
        // "23:59-23:59" equal times (single-minute window)
        assertThat(validator.isAValidRule("??.??.???? ? ? 23:59-23:59")).isTrue
    }
}