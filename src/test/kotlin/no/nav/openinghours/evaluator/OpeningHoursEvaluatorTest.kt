package no.nav.openinghours.evaluator

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class OpeningHoursEvaluatorTest {

    private val evaluator = OpeningHoursEvaluator()

    private fun rule(name: String, dsl: String) = ResolvedRule(name, dsl)
    private fun group(name: String, vararg entries: ResolvedEntry) =
        ResolvedGroup(name, entries.toList())

    @Test
    fun `isOpen sentinel and weekday rules`() {
        val basisRule5 = "??.??.???? ? 1-5 07:00-21:00"
        val basisRule6 = "??.??.???? 1-5,10-L ? 07:00-21:00"
        val helligDag = "07.04.2023 ? ? 00:00-00:00"

        val easterFriMidday = LocalDateTime.of(LocalDate.of(2023, 4, 7), LocalTime.NOON)
        val normalTueMidday = LocalDateTime.of(LocalDate.of(2023, 3, 7), LocalTime.NOON)

        assertThat(evaluator.isOpen(easterFriMidday, basisRule5)).isTrue
        assertThat(evaluator.isOpen(easterFriMidday, helligDag)).isFalse
        assertThat(evaluator.isOpen(normalTueMidday, basisRule6)).isFalse
    }

    @Test
    fun `boundary tolerance is plus minus one minute`() {
        val r = "??.??.???? ? ? 09:00-17:00"
        val date = LocalDate.of(2024, 1, 15)
        assertThat(evaluator.isOpen(LocalDateTime.of(date, LocalTime.of(8, 59)), r)).isTrue
        assertThat(evaluator.isOpen(LocalDateTime.of(date, LocalTime.of(8, 58)), r)).isFalse
        assertThat(evaluator.isOpen(LocalDateTime.of(date, LocalTime.of(17, 0)), r)).isTrue
        assertThat(evaluator.isOpen(LocalDateTime.of(date, LocalTime.of(17, 1)), r)).isTrue
        assertThat(evaluator.isOpen(LocalDateTime.of(date, LocalTime.of(17, 2)), r)).isFalse
    }

    @Test
    fun `closing time 23-59 rolls to midnight`() {
        val date = LocalDate.of(2024, 1, 15)
        val closesAtMidnight = "??.??.???? ? ? 07:00-23:59"
        val opensAtMidnight = "??.??.???? ? ? 00:00-07:00"

        assertThat(evaluator.getClosingTime("00:00-23:59")).isEqualTo(LocalTime.MIDNIGHT)
        assertThat(evaluator.getClosingTime("07:00-17:00")).isEqualTo(LocalTime.of(17, 0))

        assertThat(evaluator.isOpen(LocalDateTime.of(date, LocalTime.of(6, 59)), closesAtMidnight)).isTrue
        assertThat(evaluator.isOpen(LocalDateTime.of(date, LocalTime.of(23, 59)), closesAtMidnight)).isTrue
        assertThat(evaluator.isOpen(LocalDateTime.of(date.plusDays(1), LocalTime.of(0, 0)), closesAtMidnight)).isTrue
        assertThat(evaluator.isOpen(LocalDateTime.of(date.plusDays(1), LocalTime.of(0, 1)), closesAtMidnight)).isFalse

        assertThat(evaluator.isOpen(LocalDateTime.of(date.minusDays(1), LocalTime.of(23, 59)), opensAtMidnight)).isTrue
        assertThat(evaluator.isOpen(LocalDateTime.of(date.minusDays(1), LocalTime.of(23, 58)), opensAtMidnight)).isFalse
        assertThat(evaluator.isOpen(LocalDateTime.of(date, LocalTime.of(0, 0)), opensAtMidnight)).isTrue
    }

    @Test
    fun `nested groups first match wins - port from getOpeninghoursGroup`() {
        val r1 = rule("rule1", "17.05.???? ? ? 00:00-00:00")
        val r2 = rule("rule2", "??.??.???? L ? 07:00-18:00")
        val r3 = rule("rule3", "??.??.???? 1-5,25-30 ? 07:00-21:00")
        val r4 = rule("rule4", "??.04.???? ? 1-5 10:00-16:00")
        val r5 = rule("rule5", "24.12.2023 ? 1-5 09:00-14:00")
        val r6 = rule("rule6", "24.12.???? ? 1-5 09:00-15:00")
        val r7 = rule("rule7", "??.??.???? ? 1-5 07:30-17:00")
        val r8 = rule("rule8", "??.??.???? 12-15 ? 08:00-16:30")
        val r9 = rule("rule9", "??.??.???? 6 1-2 12:00-18:30")
        val r10 = rule("rule10", "01.05.2023 ? ? 00:00-23:59")

        val g4 = group("g4", r4, r5, r8)
        val g3 = group("g3", r10, r2, r3)
        val g2 = group("g2", g3, r9, g4)
        val g1 = group("g1", r1, g2, r6, r7)

        assertThat(evaluator.getOpeningHours(LocalDate.of(2023, 12, 24), g1)).isEqualTo("00:00-23:59")
        assertThat(evaluator.getOpeningHours(LocalDate.of(2024, 12, 24), g1)).isEqualTo("09:00-15:00")
        assertThat(evaluator.getOpeningHours(LocalDate.of(2023, 5, 17), g1)).isEqualTo("00:00-00:00")
        assertThat(evaluator.getOpeningHours(LocalDate.of(2023, 4, 24), g1)).isEqualTo("10:00-16:00")
        assertThat(evaluator.getOpeningHours(LocalDate.of(2023, 6, 6), g1)).isEqualTo("12:00-18:30")
        assertThat(evaluator.getOpeningHours(LocalDate.of(2023, 5, 25), g1)).isEqualTo("07:00-21:00")
        assertThat(evaluator.getOpeningHours(LocalDate.of(2023, 7, 22), g1)).isEqualTo("00:00-23:59")
        assertThat(evaluator.getOpeningHours(LocalDate.of(2023, 9, 30), g1)).isEqualTo("07:00-18:00")
        assertThat(evaluator.getOpeningHours(LocalDate.of(2023, 5, 1), g1)).isEqualTo("00:00-23:59")
    }

    @Test
    fun `displayData carries ruleName for matched leaf`() {
        val r = rule("weekday", "??.??.???? ? 1-5 07:30-17:00")
        val g = group("root", r)
        val data = evaluator.getDisplayData(LocalDate.of(2023, 11, 16), g)
        assertThat(data.ruleName).isEqualTo("weekday")
        assertThat(data.openingHours).isEqualTo("07:30-17:00")
    }

    @Test
    fun `empty top-level group returns open all day`() {
        val data = evaluator.getDisplayData(LocalDate.of(2023, 11, 16), group("empty"))
        assertThat(data.openingHours).isEqualTo("00:00-23:59")
        assertThat(data.ruleName).isEqualTo("No Rules stated")
    }
}