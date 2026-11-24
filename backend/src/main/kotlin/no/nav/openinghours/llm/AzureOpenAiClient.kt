package no.nav.openinghours.llm

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
import org.springframework.web.util.UriComponentsBuilder

data class ChatMessage(val role: String, val content: String)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ChatCompletionResponse(val choices: List<ChatChoice> = emptyList())

@JsonIgnoreProperties(ignoreUnknown = true)
data class ChatChoice(
    val message: ChatResponseMessage? = null,
    @JsonProperty("finish_reason") val finishReason: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ChatResponseMessage(val content: String? = null)

class LlmException(message: String, cause: Throwable? = null) : RestClientException(message, cause)

@Component
class AzureOpenAiClient(
    @Qualifier("azureOpenAiRestTemplate") private val restTemplate: RestTemplate,
    @Value("\${azure-openai.endpoint:}") private val endpoint: String,
    @Value("\${azure-openai.api-key:}") private val apiKey: String,
    @Value("\${azure-openai.deployment:}") private val deployment: String,
    @Value("\${azure-openai.api-version:2024-10-21}") private val apiVersion: String
) {
    val enabled: Boolean get() = endpoint.isNotBlank() && apiKey.isNotBlank() && deployment.isNotBlank()

    /** Sends a chat completion request in JSON mode and returns the raw JSON content of the first choice. */
    fun completeJson(messages: List<ChatMessage>): String {
        val uri = UriComponentsBuilder.fromUriString(endpoint.trimEnd('/'))
            .path("/openai/deployments/{deployment}/chat/completions")
            .queryParam("api-version", apiVersion)
            .buildAndExpand(deployment)
            .encode()
            .toUri()
        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            set("api-key", apiKey)
        }
        val body = mapOf(
            "messages" to messages,
            "response_format" to mapOf("type" to "json_object")
        )
        val response = try {
            restTemplate.postForObject(uri, HttpEntity(body, headers), ChatCompletionResponse::class.java)
        } catch (e: RestClientException) {
            throw LlmException("Azure OpenAI request failed: ${e.message}", e)
        }
        val choice = response?.choices?.firstOrNull() ?: throw LlmException("Azure OpenAI returned no choices")
        if (choice.finishReason != null && choice.finishReason != "stop") {
            throw LlmException("Azure OpenAI stopped with finish_reason=${choice.finishReason}")
        }
        return choice.message?.content?.takeIf { it.isNotBlank() }
            ?: throw LlmException("Azure OpenAI returned empty content")
    }
}
