package no.nav.openinghours.slack

import tools.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import org.springframework.web.client.RestClientException

@Component
class NewServiceSlackNotifier(
    private val slack: SlackClient,
    private val objectMapper: ObjectMapper
) {
    private val log = LoggerFactory.getLogger(NewServiceSlackNotifier::class.java)

    companion object {
        const val EVENT_TYPE = "openinghours_services_added"
        const val MAX_METADATA_BYTES = 2000
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onServicesAdded(event: ServicesAddedEvent) {
        if (!slack.enabled || event.services.isEmpty()) return
        buildMessages(event).forEach { message ->
            try {
                slack.postMessage(message)
                log.info("Sent Slack notification source={}", event.source)
            } catch (e: RestClientException) {
                log.warn("Slack notification failed source={} msg={}", event.source, e.message, e)
            }
        }
    }

    fun buildMessages(event: ServicesAddedEvent): List<SlackMessage> {
        val messages = mutableListOf<SlackMessage>()
        var batch = emptyList<AddedService>()
        event.services.forEach { service ->
            val candidate = batch + service
            if (metadataSize(event.copy(services = candidate)) > MAX_METADATA_BYTES) {
                require(batch.isNotEmpty()) { "Service ${service.id} exceeds Slack's metadata size limit" }
                messages += buildMessage(event.copy(services = batch))
                batch = listOf(service)
                require(metadataSize(event.copy(services = batch)) <= MAX_METADATA_BYTES) {
                    "Service ${service.id} exceeds Slack's metadata size limit"
                }
            } else {
                batch = candidate
            }
        }
        if (batch.isNotEmpty()) messages += buildMessage(event.copy(services = batch))
        return messages
    }

    private fun metadataSize(event: ServicesAddedEvent): Int =
        objectMapper.writeValueAsBytes(buildMessage(event).metadata).size

    private fun buildMessage(event: ServicesAddedEvent): SlackMessage {
        val listed = event.services
        val header = if (event.services.size == 1) "*New service added to OpeningHours*"
        else "*${event.services.size} new services added to OpeningHours*"
        val source = if (event.source == ServiceAddedSource.STATUSPLATTFORM_SYNC) " (Statusplattform sync)" else ""

        val text = buildString {
            append(header).append(source)
            listed.forEach {
                append("\n• *${escape(it.name)}* (${it.type}) – Team: ${escape(it.team)} – ID: `${it.id}`")
            }
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
