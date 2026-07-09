package no.nav.openinghours.service

import no.nav.openinghours.model.db.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.LocalDate
import java.util.UUID

@SpringBootTest
@Testcontainers
@Transactional
class OpeningHoursLookupServiceTest {

    companion object {
        @Container
        val postgres = PostgreSQLContainer<Nothing>("postgres:15")

        @JvmStatic
        @DynamicPropertySource
        fun overrideProps(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
        }
    }

    @Autowired lateinit var lookupService: OpeningHoursLookupService
    @Autowired lateinit var ruleRepo: RuleRepository
    @Autowired lateinit var groupRepo: OhGroupRepository

    private fun createRule(name: String, rule: String, header: String? = null, text: String? = null, onlyNav: Boolean = false): Rule {
        return ruleRepo.saveAndFlush(
            Rule.create(
                id = UUID.randomUUID(),
                name = name,
                rule = rule,
                header = header,
                text = text,
                onlyShowForNavEmployees = onlyNav
            )
        )
    }

    private fun createGroup(name: String, ruleIds: List<UUID>): OhGroup {
        return groupRepo.saveAndFlush(OhGroup.create(name = name, ruleGroupIds = ruleIds))
    }

    @Test
    fun `getOpeningHours returns time string for matching rule`() {
        // Rule matches any weekday (Mon-Fri)
        val rule = createRule("Weekdays", "??.??.???? ? 1-5 08:00-16:00")
        val group = createGroup("Office hours", listOf(rule.id))

        // 2024-03-15 is a Friday
        val result = lookupService.getOpeningHours(group.id, LocalDate.of(2024, 3, 15))
        assertThat(result).isEqualTo("08:00-16:00")
    }

    @Test
    fun `getOpeningHours returns open all day when no rule matches`() {
        // Rule only matches weekdays
        val rule = createRule("Weekdays", "??.??.???? ? 1-5 08:00-16:00")
        val group = createGroup("Office hours", listOf(rule.id))

        // 2024-03-16 is a Saturday — no match → open all day
        val result = lookupService.getOpeningHours(group.id, LocalDate.of(2024, 3, 16))
        assertThat(result).isEqualTo("00:00-23:59")
    }

    @Test
    fun `getDisplayData returns all fields including displayHeader and onlyShowForNavEmployees`() {
        val rule = createRule(
            name = "Internal hours",
            rule = "??.??.???? ? 1-5 09:00-15:00",
            header = "Intern åpningstid",
            text = "Kun for NAV-ansatte",
            onlyNav = true
        )
        val group = createGroup("Nav intern", listOf(rule.id))

        val result = lookupService.getDisplayData(group.id, LocalDate.of(2024, 3, 15))

        assertThat(result.ruleName).isEqualTo("Internal hours")
        assertThat(result.rule).isEqualTo("??.??.???? ? 1-5 09:00-15:00")
        assertThat(result.openingHours).isEqualTo("09:00-15:00")
        assertThat(result.displayHeader).isEqualTo("Intern åpningstid")
        assertThat(result.displayText).isEqualTo("Kun for NAV-ansatte")
        assertThat(result.onlyShowForNavEmployees).isTrue()
    }

    @Test
    fun `getDisplayData with non-existent group ID throws NOT_FOUND`() {
        val ex = assertThrows<ResponseStatusException> {
            lookupService.getDisplayData(UUID.randomUUID(), LocalDate.of(2024, 3, 15))
        }
        assertThat(ex.statusCode.value()).isEqualTo(404)
    }

    @Test
    fun `getDisplayData throws NOT_FOUND when group has rules but none match the date`() {
        // Rule only matches weekdays (Mon-Fri)
        val rule = createRule("Weekdays only", "??.??.???? ? 1-5 08:00-16:00")
        val group = createGroup("Office hours", listOf(rule.id))

        // 2024-03-16 is a Saturday — rule exists but does NOT match
        val ex = assertThrows<ResponseStatusException> {
            lookupService.getDisplayData(group.id, LocalDate.of(2024, 3, 16))
        }
        assertThat(ex.statusCode.value()).isEqualTo(404)
        assertThat(ex.reason).contains("has rules defined")
        assertThat(ex.reason).contains("none match")
    }

    @Test
    fun `getDisplayDataOrDefault returns default open-all-day with warning when group has no rules`() {
        val group = createGroup("Empty group", emptyList())

        val result = lookupService.getDisplayDataOrDefault(group.id, LocalDate.of(2024, 3, 16))

        assertThat(result.data.ruleName).isEqualTo("No Rules stated")
        assertThat(result.data.openingHours).isEqualTo("00:00-23:59")
        assertThat(result.warningMessage).isNotNull()
        assertThat(result.warningMessage).contains("contains no rules")
        assertThat(result.warningMessage).contains("Returned default display data")
    }

    @Test
    fun `getDisplayDataOrDefault returns data without warning when rule matches`() {
        val rule = createRule("Weekdays", "??.??.???? ? 1-5 08:00-16:00")
        val group = createGroup("Office hours", listOf(rule.id))

        // 2024-03-15 is a Friday — rule matches
        val result = lookupService.getDisplayDataOrDefault(group.id, LocalDate.of(2024, 3, 15))

        assertThat(result.warningMessage).isNull()
        assertThat(result.data.openingHours).isEqualTo("08:00-16:00")
    }

    @Test
    fun `getDisplayDataOrDefault returns default with warning when group has rules but none match`() {
        val rule = createRule("Weekdays only", "??.??.???? ? 1-5 08:00-16:00")
        val group = createGroup("Office hours", listOf(rule.id))

        // 2024-03-16 is a Saturday — no match
        val result = lookupService.getDisplayDataOrDefault(group.id, LocalDate.of(2024, 3, 16))

        assertThat(result.data.openingHours).isEqualTo("00:00-23:59")
        assertThat(result.warningMessage).isNotNull()
        assertThat(result.warningMessage).contains("has rules defined")
        assertThat(result.warningMessage).contains("none match")
    }

    @Test
    fun `getDisplayDataOrDefault returns matching rule without warning when rule matches`() {
        val rule = createRule(
            name = "Weekday hours",
            rule = "??.??.???? ? 1-5 08:00-16:00",
            header = "Weekday",
            text = "Normal hours"
        )
        val group = createGroup("Office", listOf(rule.id))

        // 2024-03-15 is a Friday — rule matches
        val result = lookupService.getDisplayDataOrDefault(group.id, LocalDate.of(2024, 3, 15))

        assertThat(result.data.ruleName).isEqualTo("Weekday hours")
        assertThat(result.data.openingHours).isEqualTo("08:00-16:00")
        assertThat(result.data.displayHeader).isEqualTo("Weekday")
        assertThat(result.data.displayText).isEqualTo("Normal hours")
        assertThat(result.warningMessage).isNull()
    }

    @Test
    fun `getDisplayDataOrDefault returns default data with warning when group has rules but none match`() {
        // Rule only matches weekdays (Mon-Fri)
        val rule = createRule("Weekdays only", "??.??.???? ? 1-5 08:00-16:00")
        val group = createGroup("Office hours", listOf(rule.id))

        // 2024-03-16 is a Saturday — rule exists but does NOT match
        val result = lookupService.getDisplayDataOrDefault(group.id, LocalDate.of(2024, 3, 16))

        assertThat(result.data).isEqualTo(no.nav.openinghours.evaluator.OpeningHoursEvaluator.DEFAULT_DISPLAY_DATA)
        assertThat(result.warningMessage).isNotNull()
        assertThat(result.warningMessage).contains("has rules defined")
        assertThat(result.warningMessage).contains("none match")
        assertThat(result.warningMessage).contains(group.id.toString())
    }

    @Test
    fun `getDisplayDataOrDefault returns default open-all-day without warning when group has no rules`() {
        val group = createGroup("Empty group", emptyList())

        val result = lookupService.getDisplayDataOrDefault(group.id, LocalDate.of(2024, 3, 16))

        assertThat(result.data.ruleName).isEqualTo("No Rules stated")
        assertThat(result.data.openingHours).isEqualTo("00:00-23:59")
        assertThat(result.data.displayHeader).isEqualTo("Default regel")
        assertThat(result.warningMessage).isNull()
    }
}

