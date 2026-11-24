package no.nav.openinghours.downtime

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class TimeWindowResolverTest {

    private val day = LocalDate.of(2026, 11, 16)
    private val posted = day.atTime(10, 20, 37)

    private fun at(time: String, date: LocalDate = day) = LocalDateTime.of(date, LocalTime.parse(time))

    private fun window(hints: TimeHints, postedAt: LocalDateTime = posted) =
        TimeWindowResolver.resolve(hints, postedAt) as ResolvedWindow.Window

    private fun range(hints: TimeHints, postedAt: LocalDateTime = posted) =
        window(hints, postedAt).let { it.start to it.end }

    @Test
    fun `start and end are used as stated`() {
        assertThat(range(TimeHints(start = at("21:30"), end = at("22:30")))).isEqualTo(at("21:30") to at("22:30"))
    }

    @Test
    fun `end before start on the same date crosses midnight`() {
        val w = window(TimeHints(start = at("18:00"), end = at("05:00")))
        assertThat(w.start to w.end).isEqualTo(at("18:00") to at("05:00", day.plusDays(1)))
        assertThat(w.assumptions).anyMatch { it.contains("next day") }
    }

    @Test
    fun `end equal to start on the same date moves to the next day`() {
        assertThat(range(TimeHints(start = at("22:00"), end = at("22:00"))))
            .isEqualTo(at("22:00") to at("22:00", day.plusDays(1)))
    }

    @Test
    fun `end before start on an explicit earlier date is rejected`() {
        val result = TimeWindowResolver.resolve(TimeHints(start = at("22:00"), end = at("21:00", day.minusDays(1))), posted)
        assertThat(result).isEqualTo(ResolvedWindow.Rejected("end is not after start"))
    }

    @Test
    fun `start plus hours`() {
        assertThat(range(TimeHints(start = at("21:00"), duration = DurationHint(2.0, DurationUnit.HOURS))))
            .isEqualTo(at("21:00") to at("23:00"))
    }

    @Test
    fun `start plus minutes`() {
        assertThat(range(TimeHints(start = at("21:00"), duration = DurationHint(40.0, DurationUnit.MINUTES))))
            .isEqualTo(at("21:00") to at("21:40"))
    }

    @Test
    fun `seconds give one minute`() {
        val w = window(TimeHints(start = at("16:30"), duration = DurationHint(10.0, DurationUnit.SECONDS)))
        assertThat(w.start to w.end).isEqualTo(at("16:30") to at("16:31"))
        assertThat(w.assumptions).anyMatch { it.contains("1 minute") }
    }

    @Test
    fun `duration only starts at message time truncated to the minute`() {
        assertThat(range(TimeHints(duration = DurationHint(2.0, DurationUnit.MINUTES))))
            .isEqualTo(at("10:20") to at("10:22"))
    }

    @Test
    fun `end and duration derive the start`() {
        assertThat(range(TimeHints(end = at("23:00"), duration = DurationHint(1.5, DurationUnit.HOURS))))
            .isEqualTo(at("21:30") to at("23:00"))
    }

    @Test
    fun `end only starts at message time`() {
        assertThat(range(TimeHints(end = at("14:00")))).isEqualTo(at("10:20") to at("14:00"))
    }

    @Test
    fun `end only before message time crosses midnight`() {
        assertThat(range(TimeHints(end = at("02:00")), day.atTime(22, 0)))
            .isEqualTo(at("22:00") to at("02:00", day.plusDays(1)))
    }

    @Test
    fun `start only lasts until midnight`() {
        assertThat(range(TimeHints(start = at("18:00")))).isEqualTo(at("18:00") to day.plusDays(1).atStartOfDay())
    }

    @Test
    fun `short start only lasts thirty minutes`() {
        assertThat(range(TimeHints(start = at("11:00"), short = true))).isEqualTo(at("11:00") to at("11:30"))
    }

    @Test
    fun `nothing stated starts at message time`() {
        assertThat(range(TimeHints(short = true))).isEqualTo(at("10:20") to at("10:50"))
        assertThat(range(TimeHints())).isEqualTo(at("10:20") to day.plusDays(1).atStartOfDay())
    }

    @Test
    fun `date only covers the whole day`() {
        val sunday = day.plusDays(6)
        assertThat(range(TimeHints(date = sunday))).isEqualTo(sunday.atStartOfDay() to sunday.plusDays(1).atStartOfDay())
    }

    @Test
    fun `date of the posting day is treated as no time`() {
        assertThat(range(TimeHints(date = day))).isEqualTo(at("10:20") to day.plusDays(1).atStartOfDay())
    }

    @Test
    fun `windows longer than fourteen days are rejected`() {
        val result = TimeWindowResolver.resolve(TimeHints(start = at("10:00"), duration = DurationHint(15.0, DurationUnit.DAYS)), posted)
        assertThat(result).isInstanceOf(ResolvedWindow.Rejected::class.java)
    }

    @Test
    fun `invalid duration is ignored`() {
        val w = window(TimeHints(start = at("21:00"), duration = DurationHint(0.0, DurationUnit.HOURS)))
        assertThat(w.end).isEqualTo(day.plusDays(1).atStartOfDay())
        assertThat(w.assumptions).anyMatch { it.contains("ignored invalid duration") }
    }

    @Test
    fun `sample messages from the channel`() {
        // Tableau: "i dag fra klokken 21 ... en til to timer"
        assertThat(range(TimeHints(start = at("21:00"), duration = DurationHint(2.0, DurationUnit.HOURS))))
            .isEqualTo(at("21:00") to at("23:00"))
        // Kelvin: "mandag etter 1630 ... nede i 10 sekunder"
        val monday = day.plusDays(7)
        assertThat(range(TimeHints(start = at("16:30", monday), duration = DurationHint(10.0, DurationUnit.SECONDS))))
            .isEqualTo(at("16:30", monday) to at("16:31", monday))
        // SysMan: "i dag klokken 11 ... litt nedetid"
        assertThat(range(TimeHints(start = at("11:00"), short = true), day.atTime(8, 2)))
            .isEqualTo(at("11:00") to at("11:30"))
        // Arena: "ganske straks en restart"
        assertThat(range(TimeHints(short = true), day.atTime(21, 13)))
            .isEqualTo(at("21:13") to at("21:43"))
        // Skatt: "kl.18:00 til ca 05:00 torsdag"
        assertThat(range(TimeHints(start = at("18:00"), end = at("05:00"))))
            .isEqualTo(at("18:00") to at("05:00", day.plusDays(1)))
    }

    @Test
    fun `oversized durations are rejected before any date arithmetic`() {
        val huge = DurationHint(1e18, DurationUnit.DAYS)
        val rejected = ResolvedWindow.Rejected("duration is longer than 14 days")
        assertThat(TimeWindowResolver.resolve(TimeHints(start = at("21:00"), duration = huge), posted)).isEqualTo(rejected)
        assertThat(TimeWindowResolver.resolve(TimeHints(end = at("21:00"), duration = huge), posted)).isEqualTo(rejected)
        assertThat(TimeWindowResolver.resolve(TimeHints(duration = DurationHint(Double.POSITIVE_INFINITY, DurationUnit.HOURS)), posted))
            .isEqualTo(rejected)
        // Ignored when start and end are both stated.
        assertThat(range(TimeHints(start = at("21:00"), end = at("22:00"), duration = huge))).isEqualTo(at("21:00") to at("22:00"))
    }
}
