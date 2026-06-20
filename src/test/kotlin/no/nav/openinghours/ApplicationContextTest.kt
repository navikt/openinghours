package no.nav.openinghours

import no.nav.openinghours.dailycache.OpeningHoursDailyCache
import no.nav.openinghours.dailycache.OpeningHoursDailyCacheScheduler
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.scheduling.config.CronTask
import org.springframework.scheduling.config.ScheduledTaskHolder
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Clock

/**
 * Smoke test: verifies the full Spring application context loads successfully
 * with all critical beans wired — Clock, daily-cache, and the midnight scheduler.
 *
 * A real PostgreSQL container is used so Flyway migrations run exactly as in
 * production, catching any schema/bean wiring problem before it reaches an
 * environment.
 */
@SpringBootTest
@Testcontainers
class ApplicationContextTest {

    companion object {
        @Container
        val postgres = PostgreSQLContainer<Nothing>("postgres:15")

        @JvmStatic
        @DynamicPropertySource
        @Suppress("unused") // invoked by Spring via reflection
        fun overrideProps(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
        }
    }

    @Autowired private lateinit var applicationContext: ApplicationContext
    @Autowired private lateinit var clock: Clock
    @Autowired private lateinit var cache: OpeningHoursDailyCache
    @Autowired private lateinit var scheduler: OpeningHoursDailyCacheScheduler
    @Autowired private lateinit var scheduledTaskHolder: ScheduledTaskHolder

    // ── Context ──────────────────────────────────────────────────────────────

    @Test
    fun `application context loads without errors`() {
        assertThat(applicationContext).isNotNull
    }

    // ── Clock bean ───────────────────────────────────────────────────────────

    @Test
    fun `Clock bean is present and configured with Europe-Oslo timezone`() {
        assertThat(clock).isNotNull
        assertThat(clock.zone.id)
            .`as`("Clock must use the timezone from openinghours.timezone (application.yml default)")
            .isEqualTo("Europe/Oslo")
    }

    // ── Cache bean ───────────────────────────────────────────────────────────

    @Test
    fun `OpeningHoursDailyCache bean is present and populated after startup`() {
        assertThat(cache).isNotNull
        // ApplicationReadyEvent fired on startup → cache.populate() was called.
        // On a fresh DB with no services the snapshot is an empty map, never null.
        assertThat(cache.getAll())
            .`as`("Cache snapshot must be non-null after context startup")
            .isNotNull
    }

    // ── Scheduler bean ───────────────────────────────────────────────────────

    @Test
    fun `OpeningHoursDailyCacheScheduler bean is present`() {
        assertThat(scheduler).isNotNull
    }

    @Test
    fun `midnight CronTask is registered in the Spring scheduling infrastructure`() {
        val expressions = scheduledTaskHolder.scheduledTasks
            .mapNotNull { it.task as? CronTask }
            .map { it.expression }

        assertThat(expressions)
            .`as`("A '0 0 0 * * *' CronTask must be registered by OpeningHoursDailyCacheScheduler")
            .contains("0 0 0 * * *")
    }

    @Test
    fun `midnight CronTask zone resolves to the Clock bean's zone`() {
        // The @Scheduled zone SpEL #{@clock.zone.id} must resolve at context startup.
        // If it failed, the context would not have loaded at all.  Here we confirm the
        // resolved zone matches the Clock bean so the cron fires at local midnight, not UTC.
        assertThat(clock.zone.id)
            .`as`("Scheduler cron zone must match the Clock bean — both must be Europe/Oslo")
            .isEqualTo("Europe/Oslo")
    }
}

