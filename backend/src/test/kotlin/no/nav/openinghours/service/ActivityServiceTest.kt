package no.nav.openinghours.service

import jakarta.persistence.EntityManager
import no.nav.openinghours.model.db.ServiceType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpStatus
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

@SpringBootTest
@Testcontainers
@Transactional
class ActivityServiceTest {

    companion object {
        @Container
        val postgres = PostgreSQLContainer("postgres:15")

        @JvmStatic
        @DynamicPropertySource
        @Suppress("unused")
        fun overrideProps(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
        }
    }

    @Autowired lateinit var activities: ActivityService
    @Autowired lateinit var services: ServiceService
    @Autowired lateinit var clock: Clock
    @Autowired lateinit var entityManager: EntityManager

    private lateinit var serviceId: UUID
    private lateinit var otherServiceId: UUID
    private val day = LocalDate.of(2030, 6, 10)

    @BeforeEach
    fun setup() {
        serviceId = services.save("activity-svc-${UUID.randomUUID()}", ServiceType.TJENESTE, "team").id
        otherServiceId = services.save("activity-other-${UUID.randomUUID()}", ServiceType.TJENESTE, "team").id
    }

    private fun input(
        start: String,
        end: String,
        date: LocalDate = day,
        endDate: LocalDate = date,
        svc: Set<UUID> = setOf(serviceId),
        header: String = "Maintenance",
        message: String = "Database upgrade"
    ) = ActivityInput(
        serviceIds = svc,
        header = header,
        message = message,
        start = date.atTime(LocalTime.parse(start)),
        end = endDate.atTime(LocalTime.parse(end))
    )

    @Test
    fun `multiple activities on the same day are returned sorted by start`() {
        activities.create(input("14:00", "15:00", header = "Second"))
        activities.create(input("08:00", "09:00", header = "First"))
        activities.create(input("10:00", "11:00", svc = setOf(otherServiceId), header = "Other service"))

        val result = activities.getForService(serviceId, day, day)

        assertThat(result.map { it.header }).containsExactly("First", "Second")
    }

    @Test
    fun `one activity can cover several services`() {
        val created = activities.create(input("08:00", "09:00", svc = setOf(serviceId, otherServiceId)))

        assertThat(created.serviceIds).containsExactlyInAnyOrder(serviceId, otherServiceId)
        assertThat(activities.getForService(serviceId, day, day).map { it.id }).containsExactly(created.id)
        assertThat(activities.getForService(otherServiceId, day, day).map { it.id }).containsExactly(created.id)
        assertThat(activities.getForAllServices(day, day)).hasSize(1)
    }

    @Test
    fun `start and end are interpreted and returned in the configured timezone`() {
        val created = activities.create(input("08:00", "09:30"))

        assertThat(clock.zone).isEqualTo(ZoneId.of("Europe/Oslo"))
        assertThat(created.start.toLocalDateTime()).isEqualTo(day.atTime(8, 0))
        assertThat(created.end.toLocalDateTime()).isEqualTo(day.atTime(9, 30))
        assertThat(created.start.offset).isEqualTo(clock.zone.rules.getOffset(created.start.toInstant()))
    }

    @Test
    fun `activity spanning midnight is returned for both days but not neighbouring days`() {
        activities.create(input("22:00", "02:00", endDate = day.plusDays(1)))

        assertThat(activities.getForService(serviceId, day, day)).hasSize(1)
        assertThat(activities.getForService(serviceId, day.plusDays(1), day.plusDays(1))).hasSize(1)
        assertThat(activities.getForService(serviceId, day.minusDays(1), day.minusDays(1))).isEmpty()
        assertThat(activities.getForService(serviceId, day.plusDays(2), day.plusDays(2))).isEmpty()
    }

    @Test
    fun `activity ending exactly at midnight is not returned for the next day`() {
        activities.create(input("22:00", "00:00", endDate = day.plusDays(1)))

        assertThat(activities.getForService(serviceId, day.plusDays(1), day.plusDays(1))).isEmpty()
    }

    @Test
    fun `range query returns activities across days and services`() {
        activities.create(input("08:00", "09:00", date = day))
        activities.create(input("08:00", "09:00", date = day.plusDays(3)))
        activities.create(input("08:00", "09:00", date = day.plusDays(10)))
        activities.create(input("08:00", "09:00", date = day.plusDays(1), svc = setOf(otherServiceId)))

        assertThat(activities.getForService(serviceId, day, day.plusDays(5))).hasSize(2)
        assertThat(activities.getForAllServices(day, day.plusDays(5))).hasSize(3)
        assertThat(activities.getForAllServices(day.plusDays(1), day.plusDays(1)).map { it.serviceIds })
            .containsExactly(listOf(otherServiceId))
    }

    @Test
    fun `today query uses the clock and marks ongoing activities`() {
        val today = LocalDate.now(clock)
        val now = clock.instant()
        val zone = clock.zone
        activities.create(
            ActivityInput(
                setOf(serviceId), "Ongoing", "Now",
                now.minusSeconds(60).atZone(zone).toLocalDateTime(),
                now.plusSeconds(3600).atZone(zone).toLocalDateTime()
            )
        )
        activities.create(input("08:00", "09:00", date = today.plusDays(1), header = "Tomorrow"))

        val result = activities.getTodayForService(serviceId)

        assertThat(result.map { it.header }).containsExactly("Ongoing")
        assertThat(result.single().ongoing).isTrue()
    }

    @Test
    fun `batch create is all or nothing`() {
        val ex = assertThrows<ResponseStatusException> {
            activities.createAll(listOf(input("08:00", "09:00"), input("10:00", "09:00")))
        }
        assertThat(ex.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        assertThat(ex.reason).startsWith("activities[1]:")
        assertThat(activities.getForService(serviceId, day, day)).isEmpty()
    }

    @Test
    fun `create rejects invalid input`() {
        fun reason(i: ActivityInput) = assertThrows<ResponseStatusException> { activities.create(i) }.reason

        assertThat(reason(input("09:00", "09:00"))).isEqualTo("End must be after start")
        assertThat(reason(input("08:00", "09:00", header = " "))).isEqualTo("Header must not be blank")
        assertThat(reason(input("08:00", "09:00", message = ""))).isEqualTo("Message must not be blank")
        assertThat(reason(input("08:00", "09:00", svc = emptySet()))).isEqualTo("At least one service is required")
        val unknown = UUID.randomUUID()
        assertThat(reason(input("08:00", "09:00", svc = setOf(serviceId, unknown))))
            .isEqualTo("Service(s) not found: $unknown")
    }

    @Test
    fun `query for unknown service returns 404 and inverted range returns 400`() {
        val notFound = assertThrows<ResponseStatusException> { activities.getForService(UUID.randomUUID(), day, day) }
        assertThat(notFound.statusCode).isEqualTo(HttpStatus.NOT_FOUND)

        val badRange = assertThrows<ResponseStatusException> { activities.getForAllServices(day, day.minusDays(1)) }
        assertThat(badRange.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
    }

    @Test
    fun `update replaces activity fields and services`() {
        val created = activities.create(input("08:00", "09:00"))

        val updated = activities.update(
            created.id,
            input("12:00", "13:00", date = day.plusDays(1), svc = setOf(otherServiceId), header = "Moved")
        )

        assertThat(updated.header).isEqualTo("Moved")
        assertThat(updated.serviceIds).containsExactly(otherServiceId)
        assertThat(updated.start.toLocalDateTime()).isEqualTo(day.plusDays(1).atTime(12, 0))
        assertThat(activities.getForService(serviceId, day, day.plusDays(1))).isEmpty()
        assertThat(activities.getForService(otherServiceId, day, day.plusDays(1))).hasSize(1)
    }

    @Test
    fun `deleting a service deletes activities linked only to it and unlinks shared ones`() {
        activities.create(input("08:00", "09:00", header = "Only this service"))
        val shared = activities.create(input("10:00", "11:00", svc = setOf(serviceId, otherServiceId), header = "Shared"))

        assertThat(services.delete(serviceId)).isTrue()
        entityManager.flush()
        entityManager.clear()

        val remaining = activities.getForAllServices(day, day)
        assertThat(remaining.map { it.id }).containsExactly(shared.id)
        assertThat(remaining.single().serviceIds).containsExactly(otherServiceId)
    }

    @Test
    fun `delete returns false for unknown activity`() {
        assertThat(activities.delete(UUID.randomUUID())).isFalse()
    }
}
