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

    private fun respond(content: String) {
        val escaped = objectMapper.writeValueAsString(content)
        server.expect(requestTo(org.hamcrest.Matchers.startsWith("https://aoai.test/")))
            .andExpect(jsonPath("$.messages[1].content").value(allOf(
                containsString("Current time: 2030-06-10T10:00:00+02:00"),
                containsString("Message posted at: 2030-06-10T09:30:00+02:00"),
                containsString("${gosys.id} | Gosys | TJENESTE | Team X"),
                containsString("Gosys er nede i kveld")
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
    fun `rejects invalid JSON from the model`() {
        respond("not json")

        assertThrows<LlmException> {
            extractor.extract("Gosys er nede i kveld", Instant.parse("2030-06-10T07:30:00Z"), listOf(gosys))
        }
    }
}
