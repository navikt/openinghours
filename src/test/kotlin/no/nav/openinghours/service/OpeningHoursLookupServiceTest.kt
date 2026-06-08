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
    fun `getDisplayData returns default open-all-day when group has no rules at all`() {
        val group = createGroup("Empty group", emptyList())

        val result = lookupService.getDisplayData(group.id, LocalDate.of(2024, 3, 16))

        assertThat(result.ruleName).isEqualTo("No Rules stated")
        assertThat(result.openingHours).isEqualTo("00:00-23:59")
        assertThat(result.displayHeader).isEqualTo("Default regel")
    }
}

