package no.nav.openinghours.evaluator

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
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

    // ── Per-year caching ──────────────────────────────────────────────────────

    @Test
    fun `holidaysForYear returns the same Set instance on repeated calls for the same year`() {
        // Verifies that the ConcurrentHashMap cache is hit on the second call,
        // i.e. no new Set is allocated and Easter is not recomputed.
        val first  = holidays.holidaysForYear(2025)
        val second = holidays.holidaysForYear(2025)
        assertThat(second).isSameAs(first)
    }

    @Test
    fun `holidaysForYear returns different Set instances for different years`() {
        val set2024 = holidays.holidaysForYear(2024)
        val set2025 = holidays.holidaysForYear(2025)
        assertThat(set2025).isNotSameAs(set2024)
        assertThat(set2025).doesNotContainAnyElementsOf(set2024)
    }

    @Test
    fun `isPublicHoliday is consistent across multiple calls for the same date`() {
        // Also exercises the cache path via isPublicHoliday.
        val easter2024 = LocalDate.of(2024, 3, 31)
        assertThat(holidays.isPublicHoliday(easter2024)).isTrue()
        assertThat(holidays.isPublicHoliday(easter2024)).isTrue() // cache hit
    }

    // ── Bounded cache: years outside [CACHE_YEAR_MIN, CACHE_YEAR_MAX] ─────────

    @Test
    fun `holidaysForYear for a year inside the window is memoised`() {
        val first  = holidays.holidaysForYear(NorwegianPublicHolidays.CACHE_YEAR_MIN)
        val second = holidays.holidaysForYear(NorwegianPublicHolidays.CACHE_YEAR_MIN)
        assertThat(second).isSameAs(first)

        val last1 = holidays.holidaysForYear(NorwegianPublicHolidays.CACHE_YEAR_MAX)
        val last2 = holidays.holidaysForYear(NorwegianPublicHolidays.CACHE_YEAR_MAX)
        assertThat(last2).isSameAs(last1)
    }

    @Test
    fun `holidaysForYear for a year outside the window is not cached`() {
        val farFuture = NorwegianPublicHolidays.CACHE_YEAR_MAX + 1
        val farPast   = NorwegianPublicHolidays.CACHE_YEAR_MIN - 1

        // Each call for an out-of-window year allocates a new Set — not the same instance.
        assertThat(holidays.holidaysForYear(farFuture)).isNotSameAs(holidays.holidaysForYear(farFuture))
        assertThat(holidays.holidaysForYear(farPast)).isNotSameAs(holidays.holidaysForYear(farPast))
    }

    @Test
    fun `holidaysForYear still returns correct results for extreme years outside the window`() {
        // Correctness must not be sacrificed for years that bypass the cache.
        // Easter 2101: computed value is 2101-04-14 (verified independently).
        val year = NorwegianPublicHolidays.CACHE_YEAR_MAX + 1  // 2101
        val set  = holidays.holidaysForYear(year)
        assertThat(set).hasSize(12)
        // Fixed holidays must always be present regardless of year.
        assertThat(set).contains(
            LocalDate.of(year, 1, 1),
            LocalDate.of(year, 5, 1),
            LocalDate.of(year, 5, 17),
            LocalDate.of(year, 12, 25),
            LocalDate.of(year, 12, 26),
        )
    }

    @Test
    fun `holidaysForYear for a very far future year computes without error`() {
        // Ensures no exception is thrown for an extreme user-supplied year from a request param.
        assertThatCode { holidays.holidaysForYear(100_000) }.doesNotThrowAnyException()
        assertThatCode { holidays.isPublicHoliday(LocalDate.of(100_000, 1, 1)) }.doesNotThrowAnyException()
    }
}
