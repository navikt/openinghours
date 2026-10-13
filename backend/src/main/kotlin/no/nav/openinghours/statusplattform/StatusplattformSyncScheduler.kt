package no.nav.openinghours.statusplattform

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class StatusplattformSyncScheduler(
    private val syncService: StatusplattformSyncService
) {
    private val log = LoggerFactory.getLogger(StatusplattformSyncScheduler::class.java)

    @Scheduled(cron = "\${statusplattform.sync.cron}", zone = "#{@clock.zone.id}")
    fun sync() {
        try {
            syncService.sync()
        } catch (e: Exception) {
            log.error("Statusplattform sync failed msg={}", e.message, e)
        }
    }
}
