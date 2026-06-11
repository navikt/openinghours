package no.nav.openinghours.evaluator

import no.nav.openinghours.model.db.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpStatus
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.UUID

@SpringBootTest
@Testcontainers
@Transactional
class OpeningHoursTreeResolverTest {

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

    @Autowired lateinit var resolver: OpeningHoursTreeResolver
    @Autowired lateinit var ruleRepo: RuleRepository
    @Autowired lateinit var groupRepo: OhGroupRepository

    // --- Helper ---
    private fun createRule(
        name: String,
        rule: String,
        header: String? = null,
        text: String? = null,
        onlyShowForNavEmployees: Boolean = false,
        redDay: Boolean = false,
    ): Rule {
        val id = UUID.randomUUID()
        return ruleRepo.saveAndFlush(Rule.create(id, name, rule, header, text, onlyShowForNavEmployees, redDay))
    }

    private fun createGroup(name: String, childIds: List<UUID> = emptyList()): OhGroup =
        groupRepo.saveAndFlush(OhGroup.create(name = name, ruleGroupIds = childIds))

    // ===================== Tests =====================

    @Test
    fun `resolves single group with one rule`() {
        val rule = createRule("weekday", "??.??.???? ? 1-5 07:00-17:00")
        val group = createGroup("standard", listOf(rule.id))

        val resolved = resolver.resolve(group.id)

        assertThat(resolved.name).isEqualTo("standard")
        assertThat(resolved.entries).hasSize(1)
        val entry = resolved.entries[0] as ResolvedRule
        assertThat(entry.name).isEqualTo("weekday")
        assertThat(entry.rule).isEqualTo("??.??.???? ? 1-5 07:00-17:00")
    }

    @Test
    fun `resolves group with multiple rules in order`() {
        val r1 = createRule("holiday", "24.12.???? ? ? 00:00-00:00")
        val r2 = createRule("weekday", "??.??.???? ? 1-5 07:00-17:00")
        val r3 = createRule("weekend", "??.??.???? ? 6-7 00:00-00:00")
        val group = createGroup("all-rules", listOf(r1.id, r2.id, r3.id))

        val resolved = resolver.resolve(group.id)

        assertThat(resolved.entries).hasSize(3)
        assertThat((resolved.entries[0] as ResolvedRule).name).isEqualTo("holiday")
        assertThat((resolved.entries[1] as ResolvedRule).name).isEqualTo("weekday")
        assertThat((resolved.entries[2] as ResolvedRule).name).isEqualTo("weekend")
    }

    @Test
    fun `resolves nested groups recursively`() {
        val r1 = createRule("inner-rule", "??.??.???? ? 1-5 09:00-15:00")
        val childGroup = createGroup("child", listOf(r1.id))

        val r2 = createRule("outer-rule", "??.??.???? ? 6 10:00-14:00")
        val parentGroup = createGroup("parent", listOf(childGroup.id, r2.id))

        val resolved = resolver.resolve(parentGroup.id)

        assertThat(resolved.name).isEqualTo("parent")
        assertThat(resolved.entries).hasSize(2)

        val nestedGroup = resolved.entries[0] as ResolvedGroup
        assertThat(nestedGroup.name).isEqualTo("child")
        assertThat(nestedGroup.entries).hasSize(1)
        assertThat((nestedGroup.entries[0] as ResolvedRule).name).isEqualTo("inner-rule")

        val outerRule = resolved.entries[1] as ResolvedRule
        assertThat(outerRule.name).isEqualTo("outer-rule")
    }

    @Test
    fun `resolves deeply nested groups (3 levels)`() {
        val rule = createRule("deep-rule", "??.??.???? ? ? 08:00-16:00")
        val level3 = createGroup("level3", listOf(rule.id))
        val level2 = createGroup("level2", listOf(level3.id))
        val level1 = createGroup("level1", listOf(level2.id))

        val resolved = resolver.resolve(level1.id)

        val l2 = resolved.entries[0] as ResolvedGroup
        assertThat(l2.name).isEqualTo("level2")
        val l3 = l2.entries[0] as ResolvedGroup
        assertThat(l3.name).isEqualTo("level3")
        val r = l3.entries[0] as ResolvedRule
        assertThat(r.name).isEqualTo("deep-rule")
    }

    @Test
    fun `circular dependency throws INTERNAL_SERVER_ERROR`() {
        // Create two groups that reference each other
        val g1Id = UUID.randomUUID()
        val g2Id = UUID.randomUUID()

        // g1 -> g2, g2 -> g1
        groupRepo.saveAndFlush(OhGroup(id = g1Id, name = "g1", ruleGroupIds = arrayOf(g2Id.toString())))
        groupRepo.saveAndFlush(OhGroup(id = g2Id, name = "g2", ruleGroupIds = arrayOf(g1Id.toString())))

        val ex = assertThrows<ResponseStatusException> { resolver.resolve(g1Id) }
        assertThat(ex.statusCode.value()).isEqualTo(500)
        assertThat(ex.reason).contains("Circular")
    }

    @Test
    fun `non-existent group ID throws NOT_FOUND`() {
        val ex = assertThrows<ResponseStatusException> { resolver.resolve(UUID.randomUUID()) }
        assertThat(ex.statusCode.value()).isEqualTo(HttpStatus.NOT_FOUND.value())
    }

    @Test
    fun `unresolved child reference throws INTERNAL_SERVER_ERROR`() {
        val bogusId = UUID.randomUUID()
        val group = createGroup("broken", listOf(bogusId))

        val ex = assertThrows<ResponseStatusException> { resolver.resolve(group.id) }
        assertThat(ex.statusCode.value()).isEqualTo(500)
        assertThat(ex.reason).contains(bogusId.toString())
    }

    @Test
    fun `empty group returns ResolvedGroup with no entries`() {
        val group = createGroup("empty")

        val resolved = resolver.resolve(group.id)

        assertThat(resolved.name).isEqualTo("empty")
        assertThat(resolved.entries).isEmpty()
    }

    @Test
    fun `new fields displayHeader, displayText, onlyShowForNavEmployees are carried through`() {
        val rule = createRule(
            name = "nav-only",
            rule = "??.??.???? ? 1-5 08:00-15:30",
            header = "Kun for NAV-ansatte",
            text = "Begrenset åpningstid",
            onlyShowForNavEmployees = true
        )
        val group = createGroup("with-fields", listOf(rule.id))

        val resolved = resolver.resolve(group.id)
        val entry = resolved.entries[0] as ResolvedRule

        assertThat(entry.displayHeader).isEqualTo("Kun for NAV-ansatte")
        assertThat(entry.displayText).isEqualTo("Begrenset åpningstid")
        assertThat(entry.onlyShowForNavEmployees).isTrue()
    }

    @Test
    fun `redDay field is carried through resolver`() {
        val rule = createRule(
            name = "christmas",
            rule = "25.12.???? ? ? 00:00-00:00",
            redDay = true,
        )
        val group = createGroup("with-red-day", listOf(rule.id))

        val resolved = resolver.resolve(group.id)
        val entry = resolved.entries[0] as ResolvedRule

        assertThat(entry.redDay).isTrue()
    }

    @Test
    fun `mixed children - group containing both rules and sub-groups in correct order`() {
        val r1 = createRule("first-rule", "01.01.???? ? ? 00:00-00:00")
        val innerRule = createRule("inner", "??.??.???? ? 1-5 09:00-16:00")
        val subGroup = createGroup("sub", listOf(innerRule.id))
        val r2 = createRule("last-rule", "??.??.???? ? ? 07:00-21:00")

        val parent = createGroup("mixed", listOf(r1.id, subGroup.id, r2.id))

        val resolved = resolver.resolve(parent.id)

        assertThat(resolved.entries).hasSize(3)
        assertThat(resolved.entries[0]).isInstanceOf(ResolvedRule::class.java)
        assertThat((resolved.entries[0] as ResolvedRule).name).isEqualTo("first-rule")

        assertThat(resolved.entries[1]).isInstanceOf(ResolvedGroup::class.java)
        assertThat((resolved.entries[1] as ResolvedGroup).name).isEqualTo("sub")

        assertThat(resolved.entries[2]).isInstanceOf(ResolvedRule::class.java)
        assertThat((resolved.entries[2] as ResolvedRule).name).isEqualTo("last-rule")
    }
}

