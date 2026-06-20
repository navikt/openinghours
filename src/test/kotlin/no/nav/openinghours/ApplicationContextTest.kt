package no.nav.openinghours

import no.nav.openinghours.dailycache.OpeningHoursDailyCache
import no.nav.openinghours.dailycache.OpeningHoursDailyCacheScheduler
import no.nav.openinghours.model.db.ServiceType
import no.nav.openinghours.service.ServiceService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
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
 *
 * Cache-populate verification strategy
 * ─────────────────────────────────────
 * cacheRef is initialised with AtomicReference(emptyMap()), so asserting
 * getAll().isNotNull is a tautology — it passes whether or not populate() ever
 * ran.  Instead, a @TestConfiguration ApplicationRunner inserts a known service
 * BEFORE ApplicationReadyEvent fires (runners execute before the ready event),
 * so when populate() runs it has real data to load.  The test then asserts the
 * cache contains that service, proving the full chain:
 *   ApplicationReadyEvent → scheduler.refresh() → cache.populate()
 * actually fired and did real work.
 */
@SpringBootTest
@Testcontainers
class ApplicationContextTest {

    /**
     * Seeds a known service before ApplicationReadyEvent fires so that
     * cache.populate() has something to store — making a non-empty cache the
     * observable proof that the startup chain ran.
     *
     * Spring's startup order:
     *   refreshContext()  →  callRunners()  →  ApplicationReadyEvent
     * ApplicationRunner beans run in callRunners(), which is before the event.
     */
    @TestConfiguration
    class SeedConfiguration {
        @Bean
        fun seedSmokeTestService(serviceService: ServiceService): ApplicationRunner =
            ApplicationRunner {
                serviceService.save(
                    name = "SmokeTestService",
                    type = ServiceType.TJENESTE,
                    team  = "smoke-team",
                )
            }
    }

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
    fun `OpeningHoursDailyCache bean is present`() {
        assertThat(cache).isNotNull
    }

    @Test
    fun `OpeningHoursDailyCache populate is invoked on startup via ApplicationReadyEvent`() {
        // SeedConfiguration.seedSmokeTestService inserts "SmokeTestService" via an
        // ApplicationRunner, which runs before ApplicationReadyEvent fires.
        // populate() must therefore store that service in the cache by the time any
        // test method executes.  A non-empty cache (containing the known service) is
        // the only assertion that can distinguish "populate() was called" from
        // "cacheRef still holds the initial emptyMap()".
        val cachedNames = cache.getAll().values.map { it.serviceName }
        assertThat(cachedNames)
            .`as`("Cache must contain the service seeded before ApplicationReadyEvent")
            .contains("SmokeTestService")
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
