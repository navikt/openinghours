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
    // "zone" is derived directly from the Clock bean so the scheduler fires at midnight
    // in the same zone used for all isOpen/today evaluations — no separate property needed.
    @Scheduled(cron = "0 0 0 * * *", zone = "#{@clock.zone.id}")
    @EventListener(ApplicationReadyEvent::class) // also on startup
    fun refresh() {
        cache.populate()
    }
}