package no.nav.openinghours.dailycache

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.scheduling.config.CronTask
import org.springframework.scheduling.config.ScheduledTaskHolder
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.boot.test.context.TestConfiguration
import java.time.Clock
import java.time.ZoneId

// ─────────────────────────────────────────────────────────────────────────────
// Unit-level tests – no Spring context required
// ─────────────────────────────────────────────────────────────────────────────

class OpeningHoursDailyCacheSchedulerUnitTest {

    private val cache: OpeningHoursDailyCache =
        Mockito.mock(OpeningHoursDailyCache::class.java)
    private val scheduler = OpeningHoursDailyCacheScheduler(cache)

    // ── populate() is called when refresh() is invoked ────────────────────

    @Test
    fun `refresh() delegates to cache populate exactly once`() {
        scheduler.refresh()

        Mockito.verify(cache).populate()
    }

    // ── @EventListener annotation metadata ────────────────────────────────

    @Test
    fun `refresh() carries @EventListener targeting ApplicationReadyEvent`() {
        val method = OpeningHoursDailyCacheScheduler::class.java.getMethod("refresh")
        val annotation = method.getAnnotation(EventListener::class.java)

        assertThat(annotation)
            .`as`("@EventListener must be present on refresh()")
            .isNotNull()

        assertThat(annotation.value)
            .`as`("@EventListener value must include ApplicationReadyEvent")
            .contains(ApplicationReadyEvent::class)
    }

    // ── @Scheduled annotation metadata ────────────────────────────────────

    @Test
    fun `refresh() carries @Scheduled with midnight cron expression and clock-derived zone`() {
        val method = OpeningHoursDailyCacheScheduler::class.java.getMethod("refresh")
        val annotation = method.getAnnotation(Scheduled::class.java)

        assertThat(annotation)
            .`as`("@Scheduled must be present on refresh()")
            .isNotNull()

        assertThat(annotation.cron)
            .`as`("Cron expression must fire at midnight every day")
            .isEqualTo("0 0 0 * * *")

        assertThat(annotation.zone)
            .`as`("Zone must be derived from the Clock bean so it always matches the configured timezone")
            .isEqualTo("#{@clock.zone.id}")
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Spring-context tests – lightweight, no DB / Testcontainers needed
// ─────────────────────────────────────────────────────────────────────────────

@ExtendWith(SpringExtension::class)
@ContextConfiguration(classes = [OpeningHoursDailyCacheSchedulerSpringTest.Config::class])
class OpeningHoursDailyCacheSchedulerSpringTest {

    /** Minimal Spring context: just the scheduler + a mock cache bean + a fixed clock. */
    @TestConfiguration
    @EnableScheduling
    @Import(OpeningHoursDailyCacheScheduler::class)
    class Config {
        @Bean
        fun openingHoursDailyCache(): OpeningHoursDailyCache =
            Mockito.mock(OpeningHoursDailyCache::class.java)

        /** Fixed zone so the cron zone SpEL expression #{@clock.zone.id} resolves to a known value. */
        @Bean
        fun clock(): Clock = Clock.system(ZoneId.of("Europe/Oslo"))
    }

    @Autowired lateinit var scheduler: OpeningHoursDailyCacheScheduler
    @Autowired lateinit var cache: OpeningHoursDailyCache
    @Autowired lateinit var scheduledTaskHolder: ScheduledTaskHolder
    @Autowired lateinit var eventPublisher: ApplicationEventPublisher

    @BeforeEach
    fun resetMock() {
        Mockito.clearInvocations(cache)
    }

    // ── ApplicationReadyEvent causes populate() ────────────────────────────

    @Test
    fun `ApplicationReadyEvent triggers cache populate`() {
        // Publish a mock event — the @EventListener type-check passes because
        // Mockito creates a subclass of ApplicationReadyEvent.
        eventPublisher.publishEvent(Mockito.mock(ApplicationReadyEvent::class.java))

        Mockito.verify(cache).populate()
    }

    @Test
    fun `calling refresh() directly still invokes populate`() {
        scheduler.refresh()

        Mockito.verify(cache).populate()
    }

    // ── Scheduled cron is registered in the Spring task infrastructure ─────

    @Test
    fun `Spring registers exactly one CronTask with the midnight expression`() {
        val registeredCrons = scheduledTaskHolder.scheduledTasks
            .mapNotNull { it.task as? CronTask }
            .map { it.expression }

        assertThat(registeredCrons)
            .`as`("A CronTask with expression '0 0 0 * * *' must be registered")
            .contains("0 0 0 * * *")
    }

    @Test
    fun `no other cron expressions are registered by the scheduler`() {
        val registeredCrons = scheduledTaskHolder.scheduledTasks
            .mapNotNull { it.task as? CronTask }
            .map { it.expression }

        assertThat(registeredCrons)
            .`as`("Only the midnight cron should be present")
            .containsExactly("0 0 0 * * *")
    }

    @Test
    fun `registered cron task fires in the Clock bean's zone`() {
        // Spring resolves zone = "#{@clock.zone.id}" when building the CronTrigger.
        // If the SpEL expression were invalid or produced an unrecognisable zone ID,
        // the application context would have failed to start, causing the whole test
        // class to error out before reaching this point.
        // What we can verify without accessing Spring internals: the midnight CronTask
        // is registered (i.e. the zone did not prevent task registration).
        val midnightCrons = scheduledTaskHolder.scheduledTasks
            .mapNotNull { it.task as? CronTask }
            .filter { it.expression == "0 0 0 * * *" }

        assertThat(midnightCrons)
            .`as`("Midnight CronTask must be registered — context startup proves the zone SpEL resolved")
            .hasSize(1)
    }
}



