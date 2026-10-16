package no.nav.openinghours.slack

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class NewServiceSlackNotifier(private val slack: SlackClient) {
    private val log = LoggerFactory.getLogger(NewServiceSlackNotifier::class.java)

    companion object {
        const val EVENT_TYPE = "openinghours_services_added"
        const val MAX_LISTED = 50
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onServicesAdded(event: ServicesAddedEvent) {
        if (!slack.enabled || event.services.isEmpty()) return
        try {
            slack.postMessage(buildMessage(event))
            log.info("Sent Slack notification for {} new service(s) source={}", event.services.size, event.source)
        } catch (e: Exception) {
            log.warn("Slack notification failed for {} new service(s) msg={}", event.services.size, e.message, e)
        }
    }

    fun buildMessage(event: ServicesAddedEvent): SlackMessage {
        val listed = event.services.take(MAX_LISTED)
        val header = if (event.services.size == 1) "*New service added to OpeningHours*"
        else "*${event.services.size} new services added to OpeningHours*"
        val source = if (event.source == ServiceAddedSource.STATUSPLATTFORM_SYNC) " (Statusplattform sync)" else ""

        val text = buildString {
            append(header).append(source)
            listed.forEach {
                append("\n• *${escape(it.name)}* (${it.type}) – Team: ${escape(it.team)} – ID: `${it.id}`")
            }
            val remaining = event.services.size - listed.size
            if (remaining > 0) append("\n…and $remaining more")
        }

        val metadata = SlackMessageMetadata(
            eventType = EVENT_TYPE,
            eventPayload = mapOf(
                "source" to event.source.name,
                "count" to event.services.size,
                "services" to listed.map {
                    mapOf("id" to it.id.toString(), "name" to it.name, "type" to it.type.name, "team" to it.team)
                }
            )
        )
        return SlackMessage(text, metadata)
    }

    private fun escape(value: String): String =
        value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}
