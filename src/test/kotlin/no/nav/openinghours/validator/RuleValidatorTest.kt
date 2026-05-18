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
    fun `validate time format`() {
        assertThat(validator.isAValidRule("??.??.???? ? ? 07:00-21:00")).isTrue
        assertThat(validator.isAValidRule("??.??.???? ? ? 00:00-21:00")).isTrue
        assertThat(validator.isAValidRule("??.??.???? ? ? 00:00-00:00")).isTrue
        assertThat(validator.isAValidRule("??.??.???? ? ? 77:29-21:00")).isFalse
        assertThat(validator.isAValidRule("??.??.???? ? ? 12:30-0b:00")).isFalse
        assertThat(validator.isAValidRule("??.??.???? ? ? 16:00-13:00")).isFalse
        assertThat(validator.isAValidRule("??.??.???? ? ? 1a:34-16:18")).isFalse
    }
}