package no.nav.openinghours.service

import no.nav.openinghours.model.db.OhGroupRepository
import no.nav.openinghours.model.db.ServiceType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
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
    @Autowired lateinit var repo: OhGroupRepository

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
        val ruleId = UUID.randomUUID()
        val otherRuleId = UUID.randomUUID()
        val group = service.save("group-with-rules", listOf(ruleId, otherRuleId))

        val updated = service.removeRuleFromGroup(group.id, ruleId)

        assertThat(updated.ruleGroupUuids).doesNotContain(ruleId)
        assertThat(updated.ruleGroupUuids).containsExactly(otherRuleId)
    }

    @Test
    fun `removeRuleFromGroup with last rule results in empty ruleGroupIds`() {
        val ruleId = UUID.randomUUID()
        val group = service.save("group-one-rule", listOf(ruleId))

        val updated = service.removeRuleFromGroup(group.id, ruleId)

        assertThat(updated.ruleGroupUuids).isEmpty()
    }

    @Test
    fun `removeRuleFromGroup throws NOT_FOUND when rule is not a member of the group`() {
        val group = service.save("group-no-rule", emptyList())

        val ex = org.junit.jupiter.api.assertThrows<ResponseStatusException> {
            service.removeRuleFromGroup(group.id, UUID.randomUUID())
        }
        assertThat(ex.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
    }

    @Test
    fun `removeRuleFromGroup throws NOT_FOUND when group does not exist`() {
        val ex = org.junit.jupiter.api.assertThrows<ResponseStatusException> {
            service.removeRuleFromGroup(UUID.randomUUID(), UUID.randomUUID())
        }
        assertThat(ex.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
    }

}