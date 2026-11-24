package no.nav.openinghours.downtime

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class SlackDowntimeScheduler(
    private val ingestion: SlackDowntimeIngestionService
) {
    private val log = LoggerFactory.getLogger(SlackDowntimeScheduler::class.java)

    @Scheduled(cron = "\${slack.downtime.poll-cron}", zone = "#{@clock.zone.id}")
    fun poll() {
        try {
            ingestion.poll()
        } catch (e: Exception) {
            log.error("Slack downtime poll failed msg={}", e.message, e)
        }
    }
}
