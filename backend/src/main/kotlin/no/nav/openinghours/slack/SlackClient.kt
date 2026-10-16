package no.nav.openinghours.slack

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestTemplate

data class SlackMessageMetadata(
    @JsonProperty("event_type") val eventType: String,
    @JsonProperty("event_payload") val eventPayload: Map<String, Any?>
)

data class SlackMessage(
    val text: String,
    val metadata: SlackMessageMetadata? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class SlackApiResponse(
    val ok: Boolean = false,
    val error: String? = null
)

class SlackApiException(message: String) : RestClientException(message)

@Component
class SlackClient(
    @Qualifier("slackRestTemplate") private val restTemplate: RestTemplate,
    @Value("\${slack.base-url}") private val baseUrl: String,
    @Value("\${slack.bot-token:}") private val botToken: String,
    @Value("\${slack.channel-id:}") private val channelId: String
) {
    val enabled: Boolean get() = botToken.isNotBlank() && channelId.isNotBlank()

    fun postMessage(message: SlackMessage) {
        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            setBearerAuth(botToken)
        }
        val body = buildMap<String, Any> {
            put("channel", channelId)
            put("text", message.text)
            message.metadata?.let { put("metadata", it) }
        }
        val response = restTemplate.postForObject(
            baseUrl.trimEnd('/') + "/chat.postMessage",
            HttpEntity(body, headers),
            SlackApiResponse::class.java
        )
        if (response?.ok != true) {
            throw SlackApiException("Slack chat.postMessage failed: ${response?.error ?: "empty response"}")
        }
    }
}
