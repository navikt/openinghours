package no.nav.openinghours.llm

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withServerError
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule

class AzureOpenAiClientTest {

    private val objectMapper = JsonMapper.builder().addModule(KotlinModule.Builder().build()).build()
    private val restTemplate = AzureOpenAiConfig().azureOpenAiRestTemplate(objectMapper)
    private val server = MockRestServiceServer.bindTo(restTemplate).build()
    private val client = AzureOpenAiClient(restTemplate, "https://aoai.test/", "secret", "gpt-4o", "2024-10-21")
    private val url = "https://aoai.test/openai/deployments/gpt-4o/chat/completions?api-version=2024-10-21"

    @Test
    fun `sends chat completion in JSON mode and returns content`() {
        server.expect(requestTo(url))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("api-key", "secret"))
            .andExpect(jsonPath("$.response_format.type").value("json_object"))
            .andExpect(jsonPath("$.temperature").doesNotExist())
            .andExpect(jsonPath("$.messages[0].role").value("system"))
            .andExpect(jsonPath("$.messages[1].content").value("hello"))
            .andRespond(withSuccess(
                """{"choices":[{"message":{"role":"assistant","content":"{\"activities\":[]}"},"finish_reason":"stop"}]}""",
                MediaType.APPLICATION_JSON
            ))

        val content = client.completeJson(listOf(ChatMessage("system", "sys"), ChatMessage("user", "hello")))

        assertThat(content).isEqualTo("""{"activities":[]}""")
        server.verify()
    }

    @Test
    fun `fails on truncated output`() {
        server.expect(requestTo(url)).andRespond(withSuccess(
            """{"choices":[{"message":{"content":"{\"activ"},"finish_reason":"length"}]}""",
            MediaType.APPLICATION_JSON
        ))

        assertThrows<LlmException> { client.completeJson(listOf(ChatMessage("user", "x"))) }
    }

    @Test
    fun `wraps HTTP errors`() {
        server.expect(requestTo(url)).andRespond(withServerError())

        assertThrows<LlmException> { client.completeJson(listOf(ChatMessage("user", "x"))) }
    }

    @Test
    fun `is disabled without full configuration`() {
        assertThat(AzureOpenAiClient(restTemplate, "", "k", "d", "v").enabled).isFalse()
        assertThat(AzureOpenAiClient(restTemplate, "e", "", "d", "v").enabled).isFalse()
        assertThat(AzureOpenAiClient(restTemplate, "e", "k", "", "v").enabled).isFalse()
        assertThat(client.enabled).isTrue()
    }
}
