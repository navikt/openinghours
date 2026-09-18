package no.nav.openinghours.service

import no.nav.openinghours.model.db.OhGroupRepository
import no.nav.openinghours.model.db.ServiceType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.server.ResponseStatusException
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Clock
import java.time.ZonedDateTime
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

@SpringBootTest
@Testcontainers
@Transactional
class OhGroupServiceTest {

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

    @Autowired lateinit var service: OhGroupService
    @Autowired lateinit var serviceService: ServiceService
    @Autowired lateinit var ruleService: RuleService
    @Autowired lateinit var repo: OhGroupRepository
    @Autowired lateinit var clock: Clock
    @Autowired lateinit var jdbcTemplate: JdbcTemplate
    @Autowired lateinit var txManager: PlatformTransactionManager
    @jakarta.persistence.PersistenceContext lateinit var entityManager: jakarta.persistence.EntityManager

    /** Bypasses JPA lifecycle callbacks (which force updated_at to "now") to backdate a group's timestamps. */
    private fun backdate(groupId: UUID, createdAt: java.time.Instant, updatedAt: java.time.Instant?) {
        jdbcTemplate.update(
            "UPDATE oh_group SET created_at = ?, updated_at = ? WHERE id = ?",
            java.sql.Timestamp.from(createdAt),
            updatedAt?.let { java.sql.Timestamp.from(it) },
            groupId
        )
        // The persistence context still holds the entity with its original (auto-assigned) timestamps;
        // clear it so subsequent reads go back to the database and see the backdated values.
        entityManager.clear()
    }

    @Test
    fun `save creates group`() {
        val group = service.save("weekdays", emptyList())
        assertThat(repo.findById(group.id)).isPresent
    }

    @Test
    fun `save with duplicate name throws CONFLICT`() {
        service.save("mygroup", emptyList())
        val ex = org.junit.jupiter.api.assertThrows<ResponseStatusException> {
            service.save("mygroup", emptyList())
        }
        assertThat(ex.statusCode).isEqualTo(HttpStatus.CONFLICT)
    }

    @Test
    fun `get non-existing throws NOT_FOUND`() {
        val ex = org.junit.jupiter.api.assertThrows<ResponseStatusException> {
            service.get(UUID.randomUUID())
        }
        assertThat(ex.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
    }

    @Test
    fun `save then get returns same group`() {
        val saved = service.save("weekend", emptyList())

        val found = service.get(saved.id)

        assertThat(found.id).isEqualTo(saved.id)
        assertThat(found.name).isEqualTo(saved.name)
    }

    @Test
    fun `getAll returns persisted groups`() {
        val first = service.save("group-one", emptyList())
        val second = service.save("group-two", emptyList())

        val groups = service.getAll()

        assertThat(groups.map { it.id }).contains(first.id, second.id)
        assertThat(groups.map { it.name }).contains(first.name, second.name)
    }

    @Test
    fun `delete removes group and cleans parent references`() {
        val child = service.save("child", emptyList())
        val parent = service.save("parent", listOf(child.id))
        service.delete(child.id)
        val reloaded = service.get(parent.id)
        assertThat(reloaded.ruleGroupUuids).doesNotContain(child.id)
    }

    @Test
    fun `update with null fields retains existing name and ruleGroupIds`() {
        val ruleGroupA = service.save("child-a", emptyList())
        val original = service.save("original-name", listOf(ruleGroupA.id))

        val updated = service.update(original.id, null, null)

        assertThat(updated.name).isEqualTo("original-name")
        assertThat(updated.ruleGroupUuids).containsExactly(ruleGroupA.id)
    }

    @Test
    fun `update with only name provided retains existing ruleGroupIds`() {
        val ruleGroupA = service.save("child-b", emptyList())
        val original = service.save("old-name", listOf(ruleGroupA.id))

        val updated = service.update(original.id, "new-name", null)

        assertThat(updated.name).isEqualTo("new-name")
        assertThat(updated.ruleGroupUuids).containsExactly(ruleGroupA.id)
    }

    @Test
    fun `update with only ruleGroupIds provided retains existing name`() {
        val ruleGroupA = service.save("child-c", emptyList())
        val original = service.save("keep-this-name", emptyList())

        val updated = service.update(original.id, null, listOf(ruleGroupA.id))

        assertThat(updated.name).isEqualTo("keep-this-name")
        assertThat(updated.ruleGroupUuids).containsExactly(ruleGroupA.id)
    }

    @Test
    fun `circular dependency is rejected`() {
        val a = service.save("a", emptyList())
        val b = service.save("b", listOf(a.id))
        val ex = org.junit.jupiter.api.assertThrows<ResponseStatusException> {
            service.update(a.id, null, listOf(b.id))
        }
        assertThat(ex.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
    }

    @Test
    fun `getOhGroupForService returns linked group`() {
        val group = service.save("linked-group", emptyList())
        val svc = serviceService.save(
            name = "test-service",
            type = ServiceType.TJENESTE,
            team = "team-test",
            ohGroupId = group.id
        )
        val result = service.getOhGroupForService(svc.id)
        assertThat(result.id).isEqualTo(group.id)
        assertThat(result.name).isEqualTo("linked-group")
    }

    @Test
    fun `getOhGroupForService throws NOT_FOUND when service has no group`() {
        val svc = serviceService.save(
            name = "no-group-service",
            type = ServiceType.TJENESTE,
            team = "team-test"
        )
        val ex = org.junit.jupiter.api.assertThrows<ResponseStatusException> {
            service.getOhGroupForService(svc.id)
        }
        assertThat(ex.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
    }

    @Test
    fun `getOhGroupForService throws NOT_FOUND for unknown service id`() {
        val ex = org.junit.jupiter.api.assertThrows<ResponseStatusException> {
            service.getOhGroupForService(UUID.randomUUID())
        }
        assertThat(ex.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
    }

    @Test
    fun `getAssociationsByGroupId throws NOT_FOUND for unknown group`() {
        val ex = org.junit.jupiter.api.assertThrows<ResponseStatusException> {
            service.getAssociationsByGroupId(UUID.randomUUID())
        }
        assertThat(ex.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
    }

    @Test
    fun `getAssociationsByGroupId returns empty lists when group has no associations`() {
        val group = service.save("lonely-group", emptyList())

        val result = service.getAssociationsByGroupId(group.id)

        assertThat(result.services).isEmpty()
        assertThat(result.groups).isEmpty()
    }

    @Test
    fun `getAssociationsByGroupId returns linked services and referencing groups`() {
        val group = service.save("assoc-group", emptyList())

        val svc1 = serviceService.save("assoc-svc1", ServiceType.TJENESTE, "team-x", ohGroupId = group.id)
        val svc2 = serviceService.save("assoc-svc2", ServiceType.KOMPONENT, "team-x", ohGroupId = group.id)
        val parent = service.save("parent-group", listOf(group.id))

        val result = service.getAssociationsByGroupId(group.id)

        assertThat(result.services.map { it.id }).containsExactlyInAnyOrder(svc1.id, svc2.id)
        assertThat(result.groups.map { it.id }).containsExactly(parent.id)
    }

    @Test
    fun `removeRuleFromGroup removes the rule and returns the updated group`() {
        val rule = ruleService.upsert("rule-to-remove", "??.??.???? ? ? 08:00-16:00", null, null)
        val otherRule = ruleService.upsert("rule-to-keep", "??.??.???? ? ? 09:00-17:00", null, null)
        val group = service.save("group-with-rules", listOf(rule.id, otherRule.id))

        val updated = service.removeRuleFromGroup(group.id, rule.id)

        assertThat(updated.ruleGroupUuids).doesNotContain(rule.id)
        assertThat(updated.ruleGroupUuids).containsExactly(otherRule.id)
    }

    @Test
    fun `removeRuleFromGroup with last rule results in empty ruleGroupIds`() {
        val rule = ruleService.upsert("sole-rule", "??.??.???? ? ? 08:00-16:00", null, null)
        val group = service.save("group-one-rule", listOf(rule.id))

        val updated = service.removeRuleFromGroup(group.id, rule.id)

        assertThat(updated.ruleGroupUuids).isEmpty()
    }

    @Test
    fun `removeRuleFromGroup throws NOT_FOUND when rule does not exist`() {
        val group = service.save("group-unknown-rule", emptyList())

        val ex = org.junit.jupiter.api.assertThrows<ResponseStatusException> {
            service.removeRuleFromGroup(group.id, UUID.randomUUID())
        }
        assertThat(ex.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        assertThat(ex.reason).contains("Rule not found")
    }

    @Test
    fun `removeRuleFromGroup throws NOT_FOUND when rule is not a member of the group`() {
        val rule = ruleService.upsert("unlinked-rule", "??.??.???? ? ? 08:00-16:00", null, null)
        val group = service.save("group-no-rule", emptyList())

        val ex = org.junit.jupiter.api.assertThrows<ResponseStatusException> {
            service.removeRuleFromGroup(group.id, rule.id)
        }
        assertThat(ex.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        assertThat(ex.reason).contains("is not a member of group")
    }

    @Test
    fun `removeRuleFromGroup throws NOT_FOUND when group does not exist`() {
        val ex = org.junit.jupiter.api.assertThrows<ResponseStatusException> {
            service.removeRuleFromGroup(UUID.randomUUID(), UUID.randomUUID())
        }
        assertThat(ex.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
    }

    @Test
    fun `removeGroupFromGroup removes the child group and returns the updated parent`() {
        val childGroup = service.save("child-to-remove", emptyList())
        val otherChild = service.save("other-child", emptyList())
        val parent = service.save("parent-group", listOf(childGroup.id, otherChild.id))

        val updated = service.removeGroupFromGroup(parent.id, childGroup.id)

        assertThat(updated.ruleGroupUuids).doesNotContain(childGroup.id)
        assertThat(updated.ruleGroupUuids).containsExactly(otherChild.id)
    }

    @Test
    fun `removeGroupFromGroup with last child results in empty ruleGroupIds`() {
        val childGroup = service.save("sole-child", emptyList())
        val parent = service.save("parent-one-child", listOf(childGroup.id))

        val updated = service.removeGroupFromGroup(parent.id, childGroup.id)

        assertThat(updated.ruleGroupUuids).isEmpty()
    }

    @Test
    fun `removeGroupFromGroup throws NOT_FOUND when child group is not a member`() {
        val parent = service.save("parent-no-child", emptyList())

        val ex = org.junit.jupiter.api.assertThrows<ResponseStatusException> {
            service.removeGroupFromGroup(parent.id, UUID.randomUUID())
        }
        assertThat(ex.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
    }

    @Test
    fun `removeGroupFromGroup throws NOT_FOUND when parent group does not exist`() {
        val ex = org.junit.jupiter.api.assertThrows<ResponseStatusException> {
            service.removeGroupFromGroup(UUID.randomUUID(), UUID.randomUUID())
        }
        assertThat(ex.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
    }

    // --- findEmptyOutdated / deleteEmptyOutdated ------------------------------------------------

    private fun oldEnoughInstant() =
        ZonedDateTime.now(clock).minusYears(OhGroupService.EMPTY_GROUP_RETENTION_YEARS).minusDays(1).toInstant()

    private fun recentInstant() =
        ZonedDateTime.now(clock).minusYears(OhGroupService.EMPTY_GROUP_RETENTION_YEARS).plusDays(1).toInstant()

    @Test
    fun `findEmptyOutdated returns empty groups whose updatedAt is old enough`() {
        val stale = service.save("empty-stale-updated", emptyList())
        backdate(stale.id, createdAt = oldEnoughInstant(), updatedAt = oldEnoughInstant())
        val fresh = service.save("empty-fresh-updated", emptyList())
        backdate(fresh.id, createdAt = oldEnoughInstant(), updatedAt = recentInstant())

        val found = service.findEmptyOutdated().map { it.id }

        assertThat(found).contains(stale.id)
        assertThat(found).doesNotContain(fresh.id)
    }

    @Test
    fun `findEmptyOutdated falls back to createdAt when updatedAt is null`() {
        val stale = service.save("empty-stale-created", emptyList())
        backdate(stale.id, createdAt = oldEnoughInstant(), updatedAt = null)
        val fresh = service.save("empty-fresh-created", emptyList())
        backdate(fresh.id, createdAt = recentInstant(), updatedAt = null)

        val found = service.findEmptyOutdated().map { it.id }

        assertThat(found).contains(stale.id)
        assertThat(found).doesNotContain(fresh.id)
    }

    @Test
    fun `findEmptyOutdated excludes non-empty groups even when old`() {
        val rule = ruleService.upsert("rule-for-nonempty-group", "??.??.???? ? ? 08:00-16:00", null, null)
        val nonEmpty = service.save("non-empty-stale", listOf(rule.id))
        backdate(nonEmpty.id, createdAt = oldEnoughInstant(), updatedAt = oldEnoughInstant())

        val found = service.findEmptyOutdated().map { it.id }

        assertThat(found).doesNotContain(nonEmpty.id)
    }

    @Test
    fun `deleteEmptyOutdated removes qualifying groups and leaves others`() {
        val stale = service.save("delete-empty-stale", emptyList())
        backdate(stale.id, createdAt = oldEnoughInstant(), updatedAt = oldEnoughInstant())
        val fresh = service.save("delete-empty-fresh", emptyList())
        backdate(fresh.id, createdAt = oldEnoughInstant(), updatedAt = recentInstant())

        val deleted = service.deleteEmptyOutdated().map { it.id }

        assertThat(deleted).contains(stale.id)
        assertThat(repo.findById(stale.id)).isEmpty
        assertThat(repo.findById(fresh.id)).isPresent
    }

    @Test
    fun `findEmptyOutdated excludes groups still linked to a service`() {
        val linked = service.save("linked-empty-stale", emptyList())
        backdate(linked.id, createdAt = oldEnoughInstant(), updatedAt = oldEnoughInstant())
        serviceService.save(
            name = "service-on-stale-group",
            type = ServiceType.TJENESTE,
            team = "team-test",
            ohGroupId = linked.id
        )

        val found = service.findEmptyOutdated().map { it.id }

        assertThat(found).doesNotContain(linked.id)
    }

    @Test
    fun `deleteEmptyOutdated leaves empty groups untouched while still linked to a service`() {
        val linked = service.save("linked-empty-stale-delete", emptyList())
        backdate(linked.id, createdAt = oldEnoughInstant(), updatedAt = oldEnoughInstant())
        val svc = serviceService.save(
            name = "service-on-stale-group-delete",
            type = ServiceType.TJENESTE,
            team = "team-test",
            ohGroupId = linked.id
        )

        val deleted = service.deleteEmptyOutdated().map { it.id }

        assertThat(deleted).doesNotContain(linked.id)
        assertThat(repo.findById(linked.id)).isPresent
        assertThat(service.getOhGroupForService(svc.id).id).isEqualTo(linked.id)
    }

    /**
     * Directly exercises the row lock added to close the race between [OhGroupService.findEmptyOutdated]
     * scanning candidates and [OhGroupService.deleteEmptyOutdated] deleting them: while one transaction
     * holds the [OhGroupRepository.findByIdForUpdate] lock on a candidate group, a concurrent attempt to
     * link a service to that same group must block, and can only proceed once the lock-holding
     * transaction has committed - by which point the group is gone, so the link insert fails instead of
     * silently attaching to a group that is about to be (or already was) deleted.
     *
     * Uses REQUIRES_NEW transactions on separate threads because the group and service must be visible
     * to a genuinely concurrent connection, which the class-level @Transactional rollback would not allow.
     */
    @Test
    fun `row lock on candidate group blocks a concurrent service link until the delete transaction completes`() {
        val requiresNew = TransactionTemplate(txManager).apply {
            propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW
        }

        val groupId = requiresNew.execute {
            service.save("race-group-${UUID.randomUUID()}", emptyList()).id
        }!!
        val serviceId = requiresNew.execute {
            serviceService.save(
                name = "race-service-${UUID.randomUUID()}",
                type = ServiceType.TJENESTE,
                team = "team-test",
                ohGroupId = null
            ).id
        }!!

        val lockAcquired = CountDownLatch(1)
        val insertAttempted = CountDownLatch(1)
        var insertFailed = false

        val lockThread = thread {
            requiresNew.execute {
                repo.findByIdForUpdate(groupId)
                lockAcquired.countDown()
                // Hold the lock until the concurrent insert has had a chance to start and block on it.
                insertAttempted.await(5, TimeUnit.SECONDS)
                Thread.sleep(300)
                jdbcTemplate.update("DELETE FROM oh_group WHERE id = ?", groupId)
            }
        }

        assertThat(lockAcquired.await(5, TimeUnit.SECONDS)).isTrue()

        val insertThread = thread {
            insertAttempted.countDown()
            try {
                requiresNew.execute {
                    jdbcTemplate.update(
                        "INSERT INTO service_oh_group (service_id, group_id) VALUES (?, ?)",
                        serviceId, groupId
                    )
                }
            } catch (e: Exception) {
                insertFailed = true
            }
        }

        lockThread.join(10_000)
        insertThread.join(10_000)

        assertThat(insertFailed)
            .withFailMessage(
                "Expected the concurrent service link insert to fail because the row lock delayed it " +
                    "until after the group was deleted"
            )
            .isTrue()

        requiresNew.execute {
            jdbcTemplate.update("DELETE FROM service_oh_group WHERE service_id = ?", serviceId)
            jdbcTemplate.update("DELETE FROM service WHERE id = ?", serviceId)
        }
    }

}