package no.nav.openinghours.service
import no.nav.openinghours.model.db.OhGroupRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.transaction.annotation.Transactional
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.UUID
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
}