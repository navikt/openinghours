package no.nav.openinghours.statusplattform

import no.nav.openinghours.dailycache.OpeningHoursDailyCache
import no.nav.openinghours.model.db.ServiceRepository
import no.nav.openinghours.model.db.ServiceType
import no.nav.openinghours.service.ServiceService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.clearInvocations
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.transaction.annotation.Transactional
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.UUID

@SpringBootTest
@Testcontainers
@Transactional
class StatusplattformSyncServiceTest {

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

    @Autowired lateinit var syncService: StatusplattformSyncService
    @Autowired lateinit var serviceService: ServiceService
    @Autowired lateinit var repo: ServiceRepository

    @MockitoBean lateinit var client: StatusplattformClient
    @MockitoBean lateinit var cache: OpeningHoursDailyCache

    private fun remote(type: ServiceType, id: UUID? = UUID.randomUUID(), name: String? = "svc-$id", team: String? = "Team X", monitorlink: String? = null) =
        StatusplattformService(type, StatusplattformServiceDto(id, name, team, monitorlink))

    @Test
    fun `inserts new services and components with Statusplattform id`() {
        val tjeneste = remote(ServiceType.TJENESTE, name = "Gosys", monitorlink = "https://monitor")
        val komponent = remote(ServiceType.KOMPONENT, name = "Database")
        `when`(client.fetchAll()).thenReturn(listOf(tjeneste, komponent))
        clearInvocations(cache)

        val result = syncService.sync()

        assertThat(result).isEqualTo(SyncResult(fetched = 2, inserted = 2, skippedExisting = 0, skippedInvalid = 0))
        val savedTjeneste = repo.findById(tjeneste.dto.id!!).orElseThrow()
        assertThat(savedTjeneste.name).isEqualTo("Gosys")
        assertThat(savedTjeneste.type).isEqualTo(ServiceType.TJENESTE)
        assertThat(savedTjeneste.team).isEqualTo("Team X")
        assertThat(savedTjeneste.monitorlink).isEqualTo("https://monitor")
        assertThat(repo.findById(komponent.dto.id!!).orElseThrow().type).isEqualTo(ServiceType.KOMPONENT)
        verify(cache).populate()
    }

    @Test
    fun `does not modify existing services`() {
        val existing = serviceService.save("Existing", ServiceType.TJENESTE, "Original team")
        `when`(client.fetchAll()).thenReturn(listOf(remote(ServiceType.TJENESTE, id = existing.id, name = "Renamed", team = "New team")))
        clearInvocations(cache)

        val result = syncService.sync()

        assertThat(result).isEqualTo(SyncResult(fetched = 1, inserted = 0, skippedExisting = 1, skippedInvalid = 0))
        repo.flush()
        val reloaded = repo.findById(existing.id).orElseThrow()
        assertThat(reloaded.name).isEqualTo("Existing")
        assertThat(reloaded.team).isEqualTo("Original team")
        verify(cache).populate()
    }

    @Test
    fun `skips service whose name and type already exist under another id`() {
        serviceService.save("Gosys", ServiceType.TJENESTE, "team")
        val conflicting = remote(ServiceType.TJENESTE, name = "Gosys")
        `when`(client.fetchAll()).thenReturn(listOf(conflicting))

        val result = syncService.sync()

        assertThat(result.inserted).isZero()
        assertThat(result.skippedExisting).isEqualTo(1)
        assertThat(repo.findById(conflicting.dto.id!!)).isEmpty
    }

    @Test
    fun `skips invalid entries and uses fallback team`() {
        val noTeam = remote(ServiceType.TJENESTE, name = "No team", team = " ")
        `when`(client.fetchAll()).thenReturn(listOf(
            remote(ServiceType.TJENESTE, id = null),
            remote(ServiceType.TJENESTE, name = ""),
            remote(ServiceType.TJENESTE, name = "x".repeat(101)),
            noTeam
        ))

        val result = syncService.sync()

        assertThat(result).isEqualTo(SyncResult(fetched = 4, inserted = 1, skippedExisting = 0, skippedInvalid = 3))
        assertThat(repo.findById(noTeam.dto.id!!).orElseThrow().team).isEqualTo(StatusplattformSyncService.UNKNOWN_TEAM)
    }

    @Test
    fun `remote failure propagates and changes nothing`() {
        val before = repo.count()
        `when`(client.fetchAll()).thenThrow(IllegalStateException("boom"))
        clearInvocations(cache)

        assertThrows<IllegalStateException> { syncService.sync() }

        assertThat(repo.count()).isEqualTo(before)
        verify(cache, never()).populate()
    }
}
