package no.nav.openinghours.evaluator

import org.springframework.stereotype.Component
import java.time.LocalDate

/**
 * Calculates Norwegian public holidays (røde dager / helligdager) for any given year.
 *
 * Fixed dates:
 *  - 1 January   – Nyttårsdag          (New Year's Day)
 *  - 1 May       – Arbeidernes dag      (Labour Day)
 *  - 17 May      – Grunnlovsdag         (Constitution Day)
 *  - 25 December – Første juledag       (Christmas Day)
 *  - 26 December – Andre juledag        (Boxing Day)
 *
 * Movable dates (relative to Easter Sunday, computed via the Anonymous Gregorian algorithm):
 *  - Easter Sunday − 3  – Skjærtorsdag  (Maundy Thursday)
 *  - Easter Sunday − 2  – Langfredag    (Good Friday)
 *  - Easter Sunday      – Første påskedag
 *  - Easter Sunday + 1  – Andre påskedag (Easter Monday)
 *  - Easter Sunday + 39 – Kristi himmelfartsdag (Ascension Day)
 *  - Easter Sunday + 49 – Første pinsedag (Whit Sunday / Pentecost)
 *  - Easter Sunday + 50 – Andre pinsedag  (Whit Monday)
 */
@Component
class NorwegianPublicHolidays {

    /** Returns true if [date] is a Norwegian public holiday. */
    fun isPublicHoliday(date: LocalDate): Boolean = date in holidaysForYear(date.year)

    /**
     * Returns the full set of Norwegian public holidays for the given [year].
     * Results are not cached here; callers that need the set for many dates in
     * the same year should retrieve it once and reuse it.
     */
    fun holidaysForYear(year: Int): Set<LocalDate> {
        val easter = easterSunday(year)
        return setOf(
            // ── Fixed ────────────────────────────────────────────────
            LocalDate.of(year, 1, 1),   // Nyttårsdag
            LocalDate.of(year, 5, 1),   // Arbeidernes dag
            LocalDate.of(year, 5, 17),  // Grunnlovsdag
            LocalDate.of(year, 12, 25), // Første juledag
            LocalDate.of(year, 12, 26), // Andre juledag

            // ── Easter-relative ──────────────────────────────────────
            easter.minusDays(3),  // Skjærtorsdag    (Maundy Thursday)
            easter.minusDays(2),  // Langfredag       (Good Friday)
            easter,               // Første påskedag  (Easter Sunday)
            easter.plusDays(1),   // Andre påskedag   (Easter Monday)
            easter.plusDays(39),  // Kristi himmelfartsdag (Ascension Day)
            easter.plusDays(49),  // Første pinsedag  (Whit Sunday)
            easter.plusDays(50),  // Andre pinsedag   (Whit Monday)
        )
    }

    /**
     * Computes Easter Sunday for [year] using the Anonymous Gregorian algorithm
     * (also known as the Meeus/Jones/Butcher algorithm).
     */
    fun easterSunday(year: Int): LocalDate {
        val a = year % 19
        val b = year / 100
        val c = year % 100
        val d = b / 4
        val e = b % 4
        val f = (b + 8) / 25
        val g = (b - f + 1) / 3
        val h = (19 * a + b - d - g + 15) % 30
        val i = c / 4
        val k = c % 4
        val l = (32 + 2 * e + 2 * i - h - k) % 7
        val m = (a + 11 * h + 22 * l) / 451
        val month = (h + l - 7 * m + 114) / 31
        val day   = ((h + l - 7 * m + 114) % 31) + 1
        return LocalDate.of(year, month, day)
    }
}

