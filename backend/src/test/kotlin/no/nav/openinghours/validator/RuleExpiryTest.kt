package no.nav.openinghours.validator

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class RuleExpiryTest {

    @Test
    fun `recurring rules belong to no year`() {
        listOf(
            "??.??.???? ? ? 08:00-16:00",
            "24.12.???? ? ? 08:00-14:00",
            "??.07.???? ? ? 10:00-15:00"
        ).forEach {
            assertThat(RuleExpiry.anchorYear(it)).describedAs(it).isNull()
            assertThat(RuleExpiry.belongsToYear(it, 2024)).describedAs(it).isFalse()
        }
    }

    @Test
    fun `dated rules resolve to their year`() {
        assertThat(RuleExpiry.anchorYear("24.12.2024 ? ? 08:00-14:00")).isEqualTo(2024)
        assertThat(RuleExpiry.anchorYear("??.02.2024 ? ? 08:00-16:00")).isEqualTo(2024)
        assertThat(RuleExpiry.anchorYear("??.??.2024 ? ? 08:00-16:00")).isEqualTo(2024)
    }

    @Test
    fun `belongsToYear matches only the selected year`() {
        val rule = "24.12.2024 ? ? 08:00-14:00"
        assertThat(RuleExpiry.belongsToYear(rule, 2024)).isTrue()
        assertThat(RuleExpiry.belongsToYear(rule, 2025)).isFalse()
        assertThat(RuleExpiry.belongsToYear(rule, 2023)).isFalse()
    }

    @Test
    fun `year must be strict yyyy`() {
        listOf(
            "24.12.???? ? ? 08:00-14:00",
            "24.12.+204 ? ? 08:00-14:00",
            "24.12.-204 ? ? 08:00-14:00",
            "24.12.24 ? ? 08:00-14:00",
            "24.12.20245 ? ? 08:00-14:00",
            "24.12 ? ? 08:00-14:00"
        ).forEach {
            assertThat(RuleExpiry.anchorYear(it)).describedAs(it).isNull()
        }
    }

    @Test
    fun `malformed date fields belong to no year`() {
        listOf("garbage ? ? 08:00-16:00", "")
            .forEach { assertThat(RuleExpiry.anchorYear(it)).describedAs(it).isNull() }
    }

    @Test
    fun `an out-of-range day or month does not affect the year`() {
        // Year selection only reads the year field; RuleValidator rejects bad days/months on write.
        assertThat(RuleExpiry.anchorYear("99.99.2020 ? ? 08:00-16:00")).isEqualTo(2020)
    }
}
