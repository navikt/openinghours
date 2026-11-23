package no.nav.openinghours.slack

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestTemplate
import org.springframework.web.util.UriComponentsBuilder

data class SlackMessageMetadata(
    @param:JsonProperty("event_type") val eventType: String,
    @param:JsonProperty("event_payload") val eventPayload: Map<String, Any?>
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

@JsonIgnoreProperties(ignoreUnknown = true)
data class SlackAttachment(
    val pretext: String? = null,
    val title: String? = null,
    val text: String? = null,
    val fallback: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class SlackHistoryMessage(
    val type: String? = null,
    val subtype: String? = null,
    val ts: String = "",
    @param:JsonProperty("thread_ts") val threadTs: String? = null,
    val text: String? = null,
    val attachments: List<SlackAttachment> = emptyList()
) {
    /** Message text including attachment text, which bots often use instead of [text]. */
    fun fullText(): String = buildList {
        text?.let(::add)
        attachments.forEach { a ->
            listOfNotNull(a.pretext, a.title, a.text ?: a.fallback).forEach(::add)
        }
    }.map { it.trim() }.filter { it.isNotEmpty() }.distinct().joinToString("\n")
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class SlackResponseMetadata(
    @param:JsonProperty("next_cursor") val nextCursor: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class SlackHistoryResponse(
    val ok: Boolean = false,
    val error: String? = null,
    val messages: List<SlackHistoryMessage> = emptyList(),
    @param:JsonProperty("has_more") val hasMore: Boolean = false,
    @param:JsonProperty("response_metadata") val responseMetadata: SlackResponseMetadata? = null
)

class SlackApiException(message: String) : RestClientException(message)

@Component
class SlackClient(
    @param:Qualifier("slackRestTemplate") private val restTemplate: RestTemplate,
    @param:Value("\${slack.base-url}") private val baseUrl: String,
    @param:Value("\${slack.bot-token:}") private val botToken: String,
    @param:Value("\${slack.channel-id:}") private val channelId: String,
    private val delivery: SlackChannelDelivery
) {
    companion object {
        const val HISTORY_PAGE_SIZE = 200
        const val MAX_HISTORY_PAGES = 50
    }

    val enabled: Boolean get() = botToken.isNotBlank() && channelId.isNotBlank()

    val hasToken: Boolean get() = botToken.isNotBlank()

    /**
     * Top-level messages in [channel] posted after [oldest] (exclusive), oldest first.
     * Requires the bot to be a member of the channel with the channels:history scope.
     */
    fun fetchChannelHistory(channel: String, oldest: String): List<SlackHistoryMessage> =
        fetchPaged("conversations.history", mapOf("channel" to channel, "oldest" to oldest))

    /** Replies in the thread started by [threadTs] posted after [oldest] (exclusive), oldest first, without the parent. */
    fun fetchThreadReplies(channel: String, threadTs: String, oldest: String): List<SlackHistoryMessage> =
        fetchPaged("conversations.replies", mapOf("channel" to channel, "ts" to threadTs, "oldest" to oldest))
            .filter { it.ts != threadTs }

    private fun fetchPaged(method: String, params: Map<String, String>): List<SlackHistoryMessage> {
        val messages = mutableListOf<SlackHistoryMessage>()
        var cursor: String? = null
        var pages = 0
        do {
            val uri = UriComponentsBuilder.fromUriString(baseUrl.trimEnd('/') + "/" + method)
                .apply { params.forEach { (k, v) -> queryParam(k, v) } }
                .queryParam("inclusive", false)
                .queryParam("limit", HISTORY_PAGE_SIZE)
                .apply { cursor?.let { queryParam("cursor", it) } }
                .encode()
                .build()
                .toUri()
            val response = delivery.send {
                restTemplate.exchange(uri, HttpMethod.GET, HttpEntity<Void>(authHeaders()), SlackHistoryResponse::class.java).body
            }
            if (response?.ok != true) {
                throw SlackApiException("Slack $method failed: ${response?.error ?: "empty response"}")
            }
            messages += response.messages
            cursor = response.responseMetadata?.nextCursor?.takeIf { response.hasMore && it.isNotBlank() }
        } while (cursor != null && ++pages < MAX_HISTORY_PAGES)
        return messages.sortedBy { it.ts.toBigDecimalOrNull() }
    }

    private fun authHeaders() = HttpHeaders().apply { setBearerAuth(botToken) }

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
        val response = delivery.send {
            restTemplate.postForObject(
                baseUrl.trimEnd('/') + "/chat.postMessage",
                HttpEntity(body, headers),
                SlackApiResponse::class.java
            )
        }
        if (response?.ok != true) {
            throw SlackApiException("Slack chat.postMessage failed: ${response?.error ?: "empty response"}")
        }
    }
}
