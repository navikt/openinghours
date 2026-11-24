package no.nav.openinghours.controllers

import io.swagger.v3.oas.annotations.Operation
import no.nav.openinghours.downtime.SlackDowntimeIngestionService
import no.nav.openinghours.downtime.SlackDowntimeMessageView
import no.nav.openinghours.downtime.SlackDowntimePollResult
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

@RestController
@RequestMapping("/api/openinghours/slack-downtime")
class SlackDowntimeController(
    private val ingestion: SlackDowntimeIngestionService
) {
    @Operation(summary = "List the most recent messages fetched from the Slack downtime channel, with extraction results")
    @GetMapping("/messages")
    fun list(@RequestParam(defaultValue = "50") limit: Int): List<SlackDowntimeMessageView> = ingestion.list(limit)

    @Operation(summary = "Fetch new Slack downtime messages and create activities from them now")
    @PostMapping("/poll")
    fun poll(): SlackDowntimePollResult {
        if (!ingestion.enabled) {
            throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Slack downtime ingestion is not configured")
        }
        return ingestion.poll()
    }

    @Operation(summary = "Re-queue a failed or not-relevant message for extraction on the next poll")
    @PostMapping("/messages/{id}/retry")
    fun retry(@PathVariable id: UUID): SlackDowntimeMessageView = ingestion.retry(id)
}
