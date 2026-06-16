package no.nav.openinghours.service
import no.nav.openinghours.model.db.OhGroupRepository
import no.nav.openinghours.model.db.RuleRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.transaction.annotation.Transactional
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.UUID
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import org.springframework.dao.DataIntegrityViolationException
@SpringBootTest
@Testcontainers
@Transactional
class RuleServiceTest {
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
        private const val VALID_RULE = "??.??.???? ? ? 08:00-16:00"
    }
    @Autowired lateinit var ruleService: RuleService
    @Autowired lateinit var groupService: OhGroupService
    @Autowired lateinit var groupRepo: OhGroupRepository
    @Autowired lateinit var ruleRepo: RuleRepository
    @Test
    fun `delete cascades and removes rule id from parent group`() {
        val rule = ruleService.upsert("rule-cascade", VALID_RULE, null, null)
        val parent = groupService.save("parent-cascade", listOf(rule.id))
        val deleted = ruleService.delete(rule.id)
        assertThat(deleted).isTrue()
        val reloaded = groupRepo.findById(parent.id).orElseThrow()
        assertThat(reloaded.ruleGroupUuids).doesNotContain(rule.id)
    }
    @Test
    fun `delete cleans rule id from every parent group that references it`() {
        val rule = ruleService.upsert("rule-multi", VALID_RULE, null, null)
        val parentA = groupService.save("parent-multi-a", listOf(rule.id))
        val parentB = groupService.save("parent-multi-b", listOf(rule.id))
        ruleService.delete(rule.id)
        assertThat(groupRepo.findById(parentA.id).orElseThrow().ruleGroupUuids)
            .doesNotContain(rule.id)
        assertThat(groupRepo.findById(parentB.id).orElseThrow().ruleGroupUuids)
            .doesNotContain(rule.id)
    }
    @Test
    fun `delete preserves other ids in a mixed parent array`() {
        val ruleToDelete = ruleService.upsert("rule-mixed-a", VALID_RULE, null, null)
        val survivingRule = ruleService.upsert("rule-mixed-b", VALID_RULE, null, null)
        val parent = groupService.save("parent-mixed", listOf(ruleToDelete.id, survivingRule.id))
        ruleService.delete(ruleToDelete.id)
        val reloaded = groupRepo.findById(parent.id).orElseThrow()
        assertThat(reloaded.ruleGroupUuids)
            .doesNotContain(ruleToDelete.id)
            .containsExactly(survivingRule.id)
    }
    @Test
    fun `delete returns false for an unknown id`() {
        val deleted = ruleService.delete(UUID.randomUUID())
        assertThat(deleted).isFalse()
    }
    @Test
    fun `delete succeeds when the rule has no parent groups`() {
        val rule = ruleService.upsert("rule-orphan", VALID_RULE, null, null)
        val deleted = ruleService.delete(rule.id)
        assertThat(deleted).isTrue()
    }

    @Test
    fun `upsert with same name updates existing rule instead of creating duplicate`() {
        val first = ruleService.upsert("unique-rule", VALID_RULE, null, null)
        val second = ruleService.upsert("unique-rule", "??.??.???? ? ? 09:00-17:00", null, null)
        assertThat(second.id).isEqualTo(first.id)  // same entity, updated
        assertThat(ruleRepo.findAll().count { it.name == "unique-rule" }).isEqualTo(1)
    }

    @Test
    fun `update to a name already taken by another rule throws 409`() {
        ruleService.upsert("taken-name", VALID_RULE, null, null)
        val other = ruleService.upsert("other-rule", VALID_RULE, null, null)

        val ex = assertThrows<ResponseStatusException> {
            ruleService.update(other.id, "taken-name", null, null, null)
        }
        assertThat(ex.statusCode.value()).isEqualTo(409)
    }

    @Test
    fun `upsert with other integrity violation returns 500`() {
        val ex = assertThrows<ResponseStatusException> {
            ruleService.upsert("x".repeat(101), VALID_RULE, null, null)
        }
        assertThat(ex.statusCode.value()).isEqualTo(500)
        assertThat(ex.reason).contains("Upsert opening hours")
        assertThat(ex.cause).isInstanceOf(DataIntegrityViolationException::class.java)
    }

    // ── redDay preservation on upsert ─────────────────────────────────────

    @Test
    fun `upsert on a new rule stores redDay as false`() {
        val rule = ruleService.upsert("new-rule-red-day", VALID_RULE, null, null)
        assertThat(rule.redDay).isFalse()
    }

    @Test
    fun `upsert on an existing rule preserves a previously stored redDay true value`() {
        // Seed the rule with redDay=true directly via the repository to simulate
        // a value that was written before manual input was removed.
        val seed = ruleRepo.saveAndFlush(
            no.nav.openinghours.model.db.Rule.create(
                UUID.randomUUID(), "preserve-red-day", VALID_RULE,
                null, null, false, redDay = true
            )
        )
        assertThat(seed.redDay).isTrue()

        // Upsert (update path) the same rule by name — must NOT clear redDay.
        val updated = ruleService.upsert("preserve-red-day", "??.??.???? ? ? 09:00-17:00", null, null)
        assertThat(updated.id).isEqualTo(seed.id)          // same entity
        assertThat(updated.rule).isEqualTo("??.??.???? ? ? 09:00-17:00") // rule updated
        assertThat(updated.redDay).isTrue()                // redDay preserved
    }

    @Test
    fun `upsert on an existing rule preserves redDay false value`() {
        val seed = ruleRepo.saveAndFlush(
            no.nav.openinghours.model.db.Rule.create(
                UUID.randomUUID(), "preserve-red-day-false", VALID_RULE,
                null, null, false, redDay = false
            )
        )

        val updated = ruleService.upsert("preserve-red-day-false", "??.??.???? ? ? 09:00-17:00", null, null)
        assertThat(updated.id).isEqualTo(seed.id)
        assertThat(updated.redDay).isFalse()
    }
}
