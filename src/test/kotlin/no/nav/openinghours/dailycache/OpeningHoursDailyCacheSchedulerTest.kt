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
            .isNotNull

        assertThat(annotation.value)
            .`as`("@EventListener value must include ApplicationReadyEvent")
            .contains(ApplicationReadyEvent::class)
    }

    // ── @Scheduled annotation metadata ────────────────────────────────────

    @Test
    fun `refresh() carries @Scheduled with midnight cron expression`() {
        val method = OpeningHoursDailyCacheScheduler::class.java.getMethod("refresh")
        val annotation = method.getAnnotation(Scheduled::class.java)

        assertThat(annotation)
            .`as`("@Scheduled must be present on refresh()")
            .isNotNull

        assertThat(annotation.cron)
            .`as`("Cron expression must fire at midnight every day")
            .isEqualTo("0 0 0 * * *")
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Spring-context tests – lightweight, no DB / Testcontainers needed
// ─────────────────────────────────────────────────────────────────────────────

@ExtendWith(SpringExtension::class)
@ContextConfiguration(classes = [OpeningHoursDailyCacheSchedulerSpringTest.Config::class])
class OpeningHoursDailyCacheSchedulerSpringTest {

    /** Minimal Spring context: just the scheduler + a mock cache bean. */
    @TestConfiguration
    @EnableScheduling
    @Import(OpeningHoursDailyCacheScheduler::class)
    class Config {
        @Bean
        fun openingHoursDailyCache(): OpeningHoursDailyCache =
            Mockito.mock(OpeningHoursDailyCache::class.java)
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
}



