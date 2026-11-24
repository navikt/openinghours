package no.nav.openinghours.downtime

import no.nav.openinghours.llm.AzureOpenAiClient
import no.nav.openinghours.llm.AzureOpenAiConfig
import no.nav.openinghours.llm.LlmException
import no.nav.openinghours.model.db.Service
import no.nav.openinghours.model.db.ServiceType
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

class LlmDowntimeExtractorTest {

    private val objectMapper = JsonMapper.builder().addModule(KotlinModule.Builder().build()).build()
    private val restTemplate = AzureOpenAiConfig().azureOpenAiRestTemplate(objectMapper)
    private val server = MockRestServiceServer.bindTo(restTemplate).build()
    private val clock = Clock.fixed(Instant.parse("2030-06-10T08:00:00Z"), ZoneId.of("Europe/Oslo"))
    private val extractor = LlmDowntimeExtractor(
        AzureOpenAiClient(restTemplate, "https://aoai.test", "k", "gpt", "2024-10-21"), objectMapper, clock
    )
    private val gosys = Service.create(id = UUID.randomUUID(), name = "Gosys", type = ServiceType.TJENESTE, team = "Team X")

    private fun respond(content: String, text: String = "Gosys er nede i kveld") {
        val escaped = objectMapper.writeValueAsString(content)
        server.expect(requestTo(org.hamcrest.Matchers.startsWith("https://aoai.test/")))
            .andExpect(jsonPath("$.messages[0].content").value(allOf(
                containsString("For cancellations outside a thread, always list the"),
                containsString("affected services, even when all announced work is cancelled."),
                containsString("Leave services empty only when a thread reply cancels everything in its parent."),
                containsString("For a partial cancellation in a thread, list only the cancelled services.")
            )))
            .andExpect(jsonPath("$.messages[1].content").value(allOf(
                containsString("Current time: 2030-06-10T10:00:00+02:00"),
                containsString("Message posted at: 2030-06-10T09:30:00+02:00"),
                containsString("${gosys.id} | Gosys | TJENESTE | Team X"),
                containsString(text)
            )))
            .andRespond(withSuccess("""{"choices":[{"message":{"content":$escaped},"finish_reason":"stop"}]}""", MediaType.APPLICATION_JSON))
    }

    @Test
    fun `sends time context and service catalog, and parses the result`() {
        val json = """{"kind":"ANNOUNCEMENT","activities":[{"services":[{"name":"Gosys","serviceId":"${gosys.id}"}],"header":"Gosys nede","message":"Oppgradering","start":"2030-06-10T20:00","end":null,"duration":{"amount":2,"unit":"HOURS"},"short":false}],"reason":"maintenance","extra":1}"""
        respond(json)

        val result = extractor.extract("Gosys er nede i kveld", Instant.parse("2030-06-10T07:30:00Z"), listOf(gosys))

        assertThat(result.raw).isEqualTo(json)
        assertThat(result.extraction.reason).isEqualTo("maintenance")
        assertThat(result.extraction.messageKind).isEqualTo(MessageKind.ANNOUNCEMENT)
        assertThat(result.extraction.activities.single()).isEqualTo(
            ExtractedActivity(
                services = listOf(ExtractedService("Gosys", gosys.id.toString())),
                header = "Gosys nede",
                message = "Oppgradering",
                start = "2030-06-10T20:00",
                duration = ExtractedDuration(2.0, "HOURS")
            )
        )
        server.verify()
    }

    @Test
    fun `top-level cancellation retains the affected service`() {
        val text = "Gosys-vedlikeholdet er avlyst"
        respond(
            """{"kind":"CANCELLATION","activities":[{"services":[{"name":"Gosys","serviceId":"${gosys.id}"}]}]}""",
            text
        )

        val result = extractor.extract(text, Instant.parse("2030-06-10T07:30:00Z"), listOf(gosys))

        assertThat(result.extraction.messageKind).isEqualTo(MessageKind.CANCELLATION)
        assertThat(result.extraction.activities.single().services)
            .containsExactly(ExtractedService("Gosys", gosys.id.toString()))
        server.verify()
    }

    @Test
    fun `full cancellation in a thread can leave service targets empty`() {
        respond("""{"kind":"CANCELLATION","activities":[{"services":[]}]}""", "Alt er avlyst")

        val result = extractor.extract(
            "Alt er avlyst", Instant.parse("2030-06-10T07:30:00Z"), listOf(gosys),
            parentText = "Gosys er nede i kveld"
        )

        assertThat(result.extraction.messageKind).isEqualTo(MessageKind.CANCELLATION)
        assertThat(result.extraction.activities.single().services).isEmpty()
        server.verify()
    }

    @Test
    fun `rejects invalid JSON from the model`() {
        respond("not json")

        assertThrows<LlmException> {
            extractor.extract("Gosys er nede i kveld", Instant.parse("2030-06-10T07:30:00Z"), listOf(gosys))
        }
    }
}
