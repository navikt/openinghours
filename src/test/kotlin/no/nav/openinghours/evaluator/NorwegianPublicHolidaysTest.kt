package no.nav.openinghours.evaluator

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate

class NorwegianPublicHolidaysTest {

    private val holidays = NorwegianPublicHolidays()

    // ── Easter Sunday (Meeus/Jones/Butcher) ──────────────────────────────────

    @Test
    fun `easterSunday returns correct date for known years`() {
        // Reference dates from https://www.timeanddate.com/calendar/
        mapOf(
            2020 to LocalDate.of(2020, 4, 12),
            2021 to LocalDate.of(2021, 4,  4),
            2022 to LocalDate.of(2022, 4, 17),
            2023 to LocalDate.of(2023, 4,  9),
            2024 to LocalDate.of(2024, 3, 31),
            2025 to LocalDate.of(2025, 4, 20),
            2026 to LocalDate.of(2026, 4,  5),
            2030 to LocalDate.of(2030, 4, 21),
        ).forEach { (year, expected) ->
            assertThat(holidays.easterSunday(year))
                .`as`("Easter Sunday $year")
                .isEqualTo(expected)
        }
    }

    // ── Fixed public holidays ─────────────────────────────────────────────────

    @Test
    fun `New Year's Day (1 January) is always a public holiday`() {
        assertThat(holidays.isPublicHoliday(LocalDate.of(2024, 1, 1))).isTrue()
        assertThat(holidays.isPublicHoliday(LocalDate.of(2025, 1, 1))).isTrue()
    }

    @Test
    fun `Labour Day (1 May) is always a public holiday`() {
        assertThat(holidays.isPublicHoliday(LocalDate.of(2024, 5, 1))).isTrue()
        assertThat(holidays.isPublicHoliday(LocalDate.of(2025, 5, 1))).isTrue()
    }

    @Test
    fun `Constitution Day (17 May) is always a public holiday`() {
        assertThat(holidays.isPublicHoliday(LocalDate.of(2024, 5, 17))).isTrue()
        assertThat(holidays.isPublicHoliday(LocalDate.of(2025, 5, 17))).isTrue()
    }

    @Test
    fun `Christmas Day (25 December) is always a public holiday`() {
        assertThat(holidays.isPublicHoliday(LocalDate.of(2024, 12, 25))).isTrue()
        assertThat(holidays.isPublicHoliday(LocalDate.of(2025, 12, 25))).isTrue()
    }

    @Test
    fun `Boxing Day (26 December) is always a public holiday`() {
        assertThat(holidays.isPublicHoliday(LocalDate.of(2024, 12, 26))).isTrue()
        assertThat(holidays.isPublicHoliday(LocalDate.of(2025, 12, 26))).isTrue()
    }

    // ── Easter-relative public holidays (2024) ────────────────────────────────
    // Easter 2024 = 31 March

    @Test
    fun `Maundy Thursday (Easter minus 3) is a public holiday`() {
        // 2024: 28 March
        assertThat(holidays.isPublicHoliday(LocalDate.of(2024, 3, 28))).isTrue()
    }

    @Test
    fun `Good Friday (Easter minus 2) is a public holiday`() {
        // 2024: 29 March
        assertThat(holidays.isPublicHoliday(LocalDate.of(2024, 3, 29))).isTrue()
    }

    @Test
    fun `Easter Sunday is a public holiday`() {
        // 2024: 31 March
        assertThat(holidays.isPublicHoliday(LocalDate.of(2024, 3, 31))).isTrue()
    }

    @Test
    fun `Easter Monday (Easter plus 1) is a public holiday`() {
        // 2024: 1 April
        assertThat(holidays.isPublicHoliday(LocalDate.of(2024, 4, 1))).isTrue()
    }

    @Test
    fun `Ascension Day (Easter plus 39) is a public holiday`() {
        // 2024: 9 May
        assertThat(holidays.isPublicHoliday(LocalDate.of(2024, 5, 9))).isTrue()
    }

    @Test
    fun `Whit Sunday (Easter plus 49) is a public holiday`() {
        // 2024: 19 May
        assertThat(holidays.isPublicHoliday(LocalDate.of(2024, 5, 19))).isTrue()
    }

    @Test
    fun `Whit Monday (Easter plus 50) is a public holiday`() {
        // 2024: 20 May
        assertThat(holidays.isPublicHoliday(LocalDate.of(2024, 5, 20))).isTrue()
    }

    // ── holidaysForYear completeness ──────────────────────────────────────────

    @Test
    fun `holidaysForYear returns exactly 12 public holidays per year`() {
        assertThat(holidays.holidaysForYear(2024)).hasSize(12)
        assertThat(holidays.holidaysForYear(2025)).hasSize(12)
    }

    @Test
    fun `holidaysForYear for 2025 contains all known dates`() {
        // Easter 2025 = 20 April
        val expected = setOf(
            LocalDate.of(2025, 1, 1),   // Nyttårsdag
            LocalDate.of(2025, 4, 17),  // Skjærtorsdag
            LocalDate.of(2025, 4, 18),  // Langfredag
            LocalDate.of(2025, 4, 20),  // Første påskedag
            LocalDate.of(2025, 4, 21),  // Andre påskedag
            LocalDate.of(2025, 5, 1),   // Arbeidernes dag
            LocalDate.of(2025, 5, 17),  // Grunnlovsdag
            LocalDate.of(2025, 5, 29),  // Kristi himmelfartsdag
            LocalDate.of(2025, 6, 8),   // Første pinsedag
            LocalDate.of(2025, 6, 9),   // Andre pinsedag
            LocalDate.of(2025, 12, 25), // Første juledag
            LocalDate.of(2025, 12, 26), // Andre juledag
        )
        assertThat(holidays.holidaysForYear(2025)).containsExactlyInAnyOrderElementsOf(expected)
    }

    // ── Non-holidays ──────────────────────────────────────────────────────────

    @Test
    fun `ordinary weekday is not a public holiday`() {
        // Tuesday 2024-03-05 — nothing special
        assertThat(holidays.isPublicHoliday(LocalDate.of(2024, 3, 5))).isFalse()
    }

    @Test
    fun `Christmas Eve (24 December) is not a public holiday`() {
        // Christmas Eve is not an official helligdag in Norway (unlike Christmas Day)
        assertThat(holidays.isPublicHoliday(LocalDate.of(2024, 12, 24))).isFalse()
    }

    @Test
    fun `day before Labour Day is not a public holiday`() {
        assertThat(holidays.isPublicHoliday(LocalDate.of(2024, 4, 30))).isFalse()
    }
}

