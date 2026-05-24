package no.nav.openinghours.service

import no.nav.openinghours.model.db.ServiceRepository
import no.nav.openinghours.model.db.ServiceType
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
class ServiceServiceTest {

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

    @Autowired lateinit var service: ServiceService
    @Autowired lateinit var groupService: OhGroupService
    @Autowired lateinit var repo: ServiceRepository

    @Test
    fun `save creates service`() {
        val s = service.save("svcA", ServiceType.TJENESTE, "team-x")
        assertThat(repo.findById(s.id)).isPresent
    }

    @Test
    fun `save with blank name throws BAD_REQUEST`() {
        val ex = assertThrows<ResponseStatusException> {
                service.save("", ServiceType.TJENESTE, "team-x")
        }
        assertThat(ex.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
    }

    @Test
    fun `duplicate name and type throws CONFLICT`() {
        service.save("dup", ServiceType.TJENESTE, "team-x")
        val ex = assertThrows<ResponseStatusException> {
                service.save("dup", ServiceType.TJENESTE, "team-x")
        }
        assertThat(ex.statusCode).isEqualTo(HttpStatus.CONFLICT)
    }

    @Test
    fun `same name with different type is allowed`() {
        service.save("samename", ServiceType.TJENESTE, "team-x")
        val s2 = service.save("samename", ServiceType.KOMPONENT, "team-x")
        assertThat(repo.findById(s2.id)).isPresent
    }

    @Test
    fun `get non-existing throws NOT_FOUND`() {
        val ex = assertThrows<ResponseStatusException> { service.get(UUID.randomUUID()) }
        assertThat(ex.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
    }

    @Test
    fun `getAll returns persisted services`() {
        val a = service.save("a", ServiceType.TJENESTE, "team-x")
        val b = service.save("b", ServiceType.KOMPONENT, "team-x")
        assertThat(service.getAll().map { it.id }).contains(a.id, b.id)
    }

    @Test
    fun `getAllByType filters`() {
        service.save("t1", ServiceType.TJENESTE, "t")
        service.save("c1", ServiceType.KOMPONENT, "t")
        assertThat(service.getAllByType(ServiceType.TJENESTE).map { it.name }).contains("t1")
        assertThat(service.getAllByType(ServiceType.TJENESTE).map { it.name }).doesNotContain("c1")
    }

    @Test
    fun `update changes fields`() {
        val s = service.save("orig", ServiceType.TJENESTE, "team-x")
        val updated = service.update(s.id, "renamed", null, "team-y", "http://m", null, "desc", null)
        assertThat(updated.name).isEqualTo("renamed")
        assertThat(updated.team).isEqualTo("team-y")
        assertThat(updated.monitorlink).isEqualTo("http://m")
        assertThat(updated.description).isEqualTo("desc")
    }

    @Test
    fun `update to a duplicate name+type throws CONFLICT`() {
        service.save("first", ServiceType.TJENESTE, "team-x")
        val second = service.save("second", ServiceType.TJENESTE, "team-x")
        val ex = assertThrows<ResponseStatusException> {
                service.update(second.id, "first", null, null, null, null, null, null)
        }
        assertThat(ex.statusCode).isEqualTo(HttpStatus.CONFLICT)
    }

    @Test
    fun `delete removes service`() {
        val s = service.save("todelete", ServiceType.TJENESTE, "team-x")
        assertThat(service.delete(s.id)).isTrue()
        assertThrows<ResponseStatusException> { service.get(s.id) }
    }

    @Test
    fun `delete on unknown returns false`() {
        assertThat(service.delete(UUID.randomUUID())).isFalse()
    }

    @Test
    fun `setOhGroup links service to group`() {
        val s = service.save("svc-oh", ServiceType.TJENESTE, "team-x")
        val g = groupService.save("g1", emptyList())
        service.setOhGroup(s.id, g.id)
        assertThat(service.getOhGroupIdsForService(s.id)).contains(g.id)
    }

    @Test
    fun `setOhGroup with unknown group throws BAD_REQUEST`() {
        val s = service.save("svc-oh2", ServiceType.TJENESTE, "team-x")
        val ex = assertThrows<ResponseStatusException> {
                service.setOhGroup(s.id, UUID.randomUUID())
        }
        assertThat(ex.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
    }

    @Test
    fun `removeOhGroup unlinks all groups`() {
        val s = service.save("svc-oh3", ServiceType.TJENESTE, "team-x")
        val g = groupService.save("g2", emptyList())
        service.setOhGroup(s.id, g.id)
        service.removeOhGroup(s.id)
        assertThat(service.getOhGroupIdsForService(s.id)).isEmpty()
    }

    @Test
    fun `delete also clears oh group links`() {
        val s = service.save("svc-oh4", ServiceType.TJENESTE, "team-x")
        val g = groupService.save("g3", emptyList())
        service.setOhGroup(s.id, g.id)
        service.delete(s.id)
        // re-create with same name should now succeed (no orphan FK rows)
        service.save("svc-oh4", ServiceType.TJENESTE, "team-x")
    }

    @Test
    fun `setOhGroup on non-existent service throws NOT_FOUND`() {
        val g = groupService.save("g-404", emptyList())
        val ex = assertThrows<ResponseStatusException> {
            service.setOhGroup(UUID.randomUUID(), g.id)
        }
        assertThat(ex.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
    }

    @Test
    fun `setOhGroup with non-existent group throws BAD_REQUEST`() {
        val s = service.save("svc-bad-group", ServiceType.TJENESTE, "team-x")
        val ex = assertThrows<ResponseStatusException> {
            service.setOhGroup(s.id, UUID.randomUUID())
        }
        assertThat(ex.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
    }

    @Test
    fun `setOhGroup replaces previous group link`() {
        val s = service.save("svc-replace", ServiceType.TJENESTE, "team-x")
        val g1 = groupService.save("g-first", emptyList())
        val g2 = groupService.save("g-second", emptyList())

        service.setOhGroup(s.id, g1.id)
        assertThat(service.getOhGroupIdsForService(s.id)).containsExactly(g1.id)

        service.setOhGroup(s.id, g2.id)
        assertThat(service.getOhGroupIdsForService(s.id)).containsExactly(g2.id)
    }

    @Test
    fun `removeOhGroup is idempotent when no group is linked`() {
        val s = service.save("svc-no-group", ServiceType.TJENESTE, "team-x")
        // No group linked — should not throw
        service.removeOhGroup(s.id)
        assertThat(service.getOhGroupIdsForService(s.id)).isEmpty()
    }

    @Test
    fun `getResolvedOhGroupForService returns null when no group is assigned`() {
        val s = service.save("svc-no-resolve", ServiceType.TJENESTE, "team-x")
        assertThat(service.getResolvedOhGroupForService(s.id)).isNull()
    }

    @Test
    fun `getResolvedOhGroupForService returns resolved group when assigned`() {
        val s = service.save("svc-resolve", ServiceType.TJENESTE, "team-x")
        val g = groupService.save("g-resolve", emptyList())
        service.setOhGroup(s.id, g.id)

        val resolved = service.getResolvedOhGroupForService(s.id)
        assertThat(resolved).isNotNull
        assertThat(resolved!!.name).isEqualTo("g-resolve")
    }

    @Test
    fun `getAllServicesWithOpeningHours returns empty map when no assignments`() {
        // Create services without linking any group
        service.save("svc-nolink1", ServiceType.TJENESTE, "team-x")
        service.save("svc-nolink2", ServiceType.KOMPONENT, "team-x")

        assertThat(service.getAllServicesWithOpeningHours()).isEmpty()
    }

    @Test
    fun `getAllServicesWithOpeningHours returns correct map for multiple services`() {
        val s1 = service.save("svc-map1", ServiceType.TJENESTE, "team-x")
        val s2 = service.save("svc-map2", ServiceType.KOMPONENT, "team-x")
        val g1 = groupService.save("g-map1", emptyList())
        val g2 = groupService.save("g-map2", emptyList())

        service.setOhGroup(s1.id, g1.id)
        service.setOhGroup(s2.id, g2.id)

        val result = service.getAllServicesWithOpeningHours()
        assertThat(result).hasSize(2)
        assertThat(result).containsKey(s1.id)
        assertThat(result).containsKey(s2.id)
        assertThat(result[s1.id]!!.name).isEqualTo("g-map1")
        assertThat(result[s2.id]!!.name).isEqualTo("g-map2")
    }
}
