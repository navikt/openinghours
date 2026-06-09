package no.nav.openinghours.dailycache

import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component


@EnableScheduling
@Component
class OpeningHoursDailyCacheScheduler(
    private val cache: OpeningHoursDailyCache,
) {
    @Scheduled(cron = "0 0 0 * * *") // midnight every day
    @EventListener(ApplicationReadyEvent::class) // also on startup
    fun refresh() {
        cache.populate()
    }
}