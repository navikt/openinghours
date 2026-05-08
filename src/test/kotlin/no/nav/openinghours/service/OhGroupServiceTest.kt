package no.nav.openinghours.service

import no.nav.openinghours.model.db.OhGroupRepository
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

}