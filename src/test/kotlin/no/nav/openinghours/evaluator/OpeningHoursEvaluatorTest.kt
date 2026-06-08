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
        val data = evaluator.getDisplayData(LocalDate.of(2023, 11, 16), g)!!
        assertThat(data.ruleName).isEqualTo("weekday")
        assertThat(data.openingHours).isEqualTo("07:30-17:00")
    }

    @Test
    fun `empty top-level group returns open all day`() {
        val data = evaluator.getDisplayData(LocalDate.of(2023, 11, 16), group("empty"))!!
        assertThat(data.openingHours).isEqualTo("00:00-23:59")
        assertThat(data.ruleName).isEqualTo("No Rules stated")
    }

    @Test
    fun `month-only wildcard matches any weekday in that month`() {
        val aprilRule = "??.04.???? ? 1-5 10:00-16:00"

        // April weekday (Wed 2024-04-03) → matches
        assertThat(evaluator.isOpen(LocalDateTime.of(2024, 4, 3, 12, 0), aprilRule)).isTrue
        // April weekend (Sat 2024-04-06) → weekday constraint fails
        assertThat(evaluator.isOpen(LocalDateTime.of(2024, 4, 6, 12, 0), aprilRule)).isFalse
        // March weekday (Mon 2024-03-04) → month doesn't match
        assertThat(evaluator.isOpen(LocalDateTime.of(2024, 3, 4, 12, 0), aprilRule)).isFalse
        // April weekday outside hours → time constraint fails
        assertThat(evaluator.isOpen(LocalDateTime.of(2024, 4, 3, 9, 58), aprilRule)).isFalse

        // Standalone group evaluation
        val r = rule("april-hours", aprilRule)
        val g = group("root", r)
        assertThat(evaluator.getOpeningHours(LocalDate.of(2024, 4, 3), g)).isEqualTo("10:00-16:00")
        // Weekend → no match → open all day
        assertThat(evaluator.getOpeningHours(LocalDate.of(2024, 4, 6), g)).isEqualTo("00:00-23:59")
        // Wrong month → no match → open all day
        assertThat(evaluator.getOpeningHours(LocalDate.of(2024, 5, 3), g)).isEqualTo("00:00-23:59")
    }

    @Test
    fun `year-specific rule takes priority over year-wildcard when ordered first`() {
        // 2024-12-24 is a Tuesday (weekday)
        val specific2024 = rule("xmas-2024", "24.12.2024 ? 1-5 09:00-14:00")
        val wildcardYear = rule("xmas-general", "24.12.???? ? 1-5 09:00-15:00")
        val g = group("root", specific2024, wildcardYear)

        // Specific year matches first
        assertThat(evaluator.getOpeningHours(LocalDate.of(2024, 12, 24), g)).isEqualTo("09:00-14:00")
        // 2025-12-24 is a Wednesday — specific doesn't match, wildcard does
        assertThat(evaluator.getOpeningHours(LocalDate.of(2025, 12, 24), g)).isEqualTo("09:00-15:00")
    }

    @Test
    fun `year-wildcard matches across arbitrary years`() {
        val wildcardYear = rule("xmas-general", "24.12.???? ? ? 09:00-15:00")
        val g = group("root", wildcardYear)

        assertThat(evaluator.getOpeningHours(LocalDate.of(2030, 12, 24), g)).isEqualTo("09:00-15:00")
        assertThat(evaluator.getOpeningHours(LocalDate.of(2020, 12, 24), g)).isEqualTo("09:00-15:00")
        // Different date → no match
        assertThat(evaluator.getOpeningHours(LocalDate.of(2030, 12, 25), g)).isEqualTo("00:00-23:59")
    }

    @Test
    fun `getOpeningTime returns parsed opening time`() {
        assertThat(evaluator.getOpeningTime("07:00-21:00")).isEqualTo(LocalTime.of(7, 0))
        assertThat(evaluator.getOpeningTime("09:30-17:00")).isEqualTo(LocalTime.of(9, 30))
        assertThat(evaluator.getOpeningTime("00:00-23:59")).isEqualTo(LocalTime.MIDNIGHT)
        assertThat(evaluator.getOpeningTime("12:00-18:30")).isEqualTo(LocalTime.of(12, 0))
        assertThat(evaluator.getOpeningTime("00:00-00:00")).isEqualTo(LocalTime.MIDNIGHT)
    }

    @Test
    fun `getDisplayData propagates displayHeader displayText and onlyShowForNavEmployees`() {
        val r = ResolvedRule(
            name = "internal",
            rule = "??.??.???? ? 1-5 09:00-15:00",
            displayHeader = "Intern åpningstid",
            displayText = "Kun for NAV-ansatte",
            onlyShowForNavEmployees = true
        )
        val g = group("root", r)

        val data = evaluator.getDisplayData(LocalDate.of(2024, 3, 15), g) // Friday
        assertThat(data!!.ruleName).isEqualTo("internal")
        assertThat(data.openingHours).isEqualTo("09:00-15:00")
        assertThat(data.displayHeader).isEqualTo("Intern åpningstid")
        assertThat(data.displayText).isEqualTo("Kun for NAV-ansatte")
        assertThat(data.onlyShowForNavEmployees).isTrue()
    }

    @Test
    fun `getDisplayData returns null displayHeader and displayText when not set`() {
        val r = ResolvedRule(name = "basic", rule = "??.??.???? ? 1-5 08:00-16:00")
        val g = group("root", r)

        val data = evaluator.getDisplayData(LocalDate.of(2024, 3, 15), g) // Friday
        assertThat(data!!.displayHeader).isNull()
        assertThat(data.displayText).isNull()
        assertThat(data.onlyShowForNavEmployees).isFalse()
    }

    @Test
    fun `getDisplayData returns NoRules default when group contains only empty sub-groups`() {
        // A group whose only entries are empty sub-groups has no rules anywhere in the tree.
        // It must return the NoRules default (open all day), NOT null (which would cause a 404).
        val emptyChild1 = group("empty-child-1")
        val emptyChild2 = group("empty-child-2")
        val parent = group("parent-of-empties", emptyChild1, emptyChild2)

        val data = evaluator.getDisplayData(LocalDate.of(2024, 6, 10), parent)!!
        assertThat(data.openingHours).isEqualTo("00:00-23:59")
        assertThat(data.ruleName).isEqualTo("No Rules stated")
    }

    @Test
    fun `getDisplayData returns null only when rules exist but none match - mixed empty and real sub-groups`() {
        // The real sub-group has a weekday-only rule; Saturday should yield null (NoMatch), not NoRules default.
        val emptyChild = group("empty-child")
        val realChild = group("real-child", rule("weekday", "??.??.???? ? 1-5 08:00-16:00"))
        val parent = group("parent", emptyChild, realChild)

        // Saturday — the real rule exists but does not match → null
        assertThat(evaluator.getDisplayData(LocalDate.of(2024, 3, 16), parent)).isNull()
        // Monday — the real rule matches → non-null
        assertThat(evaluator.getDisplayData(LocalDate.of(2024, 3, 18), parent)).isNotNull()
    }

    @Test
    fun `getDisplayData returns NoRules default when group contains only deeply nested empty sub-groups`() {
        val level3 = group("level3")
        val level2 = group("level2", level3)
        val level1 = group("level1", level2)

        val data = evaluator.getDisplayData(LocalDate.of(2024, 6, 10), level1)!!
        assertThat(data.openingHours).isEqualTo("00:00-23:59")
        assertThat(data.ruleName).isEqualTo("No Rules stated")
    }

    @Test
    fun `getDisplayData returns null when rules exist but none match the date`() {
        val r = ResolvedRule(name = "weekday-only", rule = "??.??.???? ? 1-5 08:00-16:00")
        val g = group("root", r)

        // Saturday — rules exist but none match → null
        val data = evaluator.getDisplayData(LocalDate.of(2024, 3, 16), g)
        assertThat(data).isNull()
    }
}