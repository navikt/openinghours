package no.nav.openinghours.slack

import no.nav.openinghours.model.db.ServiceType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.client.ExpectedCount.never
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestTemplate
import java.util.UUID

class NewServiceSlackNotifierTest {

    private val restTemplate = RestTemplate()
    private val server = MockRestServiceServer.bindTo(restTemplate).build()

    private fun notifier(token: String = "xoxb-test", channel: String = "C123") =
        NewServiceSlackNotifier(SlackClient(restTemplate, "http://slack.test/api/", token, channel))

    private val id = UUID.randomUUID()
    private val event = ServicesAddedEvent(
        listOf(AddedService(id, "Gosys <beta>", ServiceType.TJENESTE, "Team X")),
        ServiceAddedSource.API
    )

    @Test
    fun `posts message with id, name and team as text and metadata`() {
        server.expect(requestTo("http://slack.test/api/chat.postMessage"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer xoxb-test"))
            .andExpect(jsonPath("$.channel").value("C123"))
            .andExpect(jsonPath("$.text").value(org.hamcrest.Matchers.containsString("Gosys &lt;beta&gt;")))
            .andExpect(jsonPath("$.text").value(org.hamcrest.Matchers.containsString("Team X")))
            .andExpect(jsonPath("$.text").value(org.hamcrest.Matchers.containsString(id.toString())))
            .andExpect(jsonPath("$.metadata.event_type").value(NewServiceSlackNotifier.EVENT_TYPE))
            .andExpect(jsonPath("$.metadata.event_payload.services[0].id").value(id.toString()))
            .andExpect(jsonPath("$.metadata.event_payload.services[0].name").value("Gosys <beta>"))
            .andExpect(jsonPath("$.metadata.event_payload.services[0].team").value("Team X"))
            .andExpect(jsonPath("$.metadata.event_payload.services[0].type").value("TJENESTE"))
            .andRespond(withSuccess("""{"ok":true}""", MediaType.APPLICATION_JSON))

        notifier().onServicesAdded(event)

        server.verify()
    }

    @Test
    fun `does nothing when Slack is not configured`() {
        server.expect(never(), requestTo("http://slack.test/api/chat.postMessage"))

        notifier(token = "").onServicesAdded(event)
        notifier(channel = "").onServicesAdded(event)

        server.verify()
    }

    @Test
    fun `swallows Slack errors so service creation is unaffected`() {
        server.expect(requestTo("http://slack.test/api/chat.postMessage"))
            .andRespond(withSuccess("""{"ok":false,"error":"channel_not_found"}""", MediaType.APPLICATION_JSON))

        notifier().onServicesAdded(event)

        server.verify()
    }

    @Test
    fun `client throws when Slack responds with ok false`() {
        server.expect(requestTo("http://slack.test/api/chat.postMessage"))
            .andRespond(withSuccess("""{"ok":false,"error":"not_in_channel"}""", MediaType.APPLICATION_JSON))

        val client = SlackClient(restTemplate, "http://slack.test/api", "xoxb-test", "C123")
        val e = assertThrows<SlackApiException> { client.postMessage(SlackMessage("hi")) }
        assertThat(e.message).contains("not_in_channel")
    }

    @Test
    fun `caps listed services and reports remainder`() {
        val many = (1..NewServiceSlackNotifier.MAX_LISTED + 3).map {
            AddedService(UUID.randomUUID(), "svc-$it", ServiceType.KOMPONENT, "team")
        }

        val message = notifier().buildMessage(ServicesAddedEvent(many, ServiceAddedSource.STATUSPLATTFORM_SYNC))
        val metadata = requireNotNull(message.metadata)

        assertThat(message.text).startsWith("*${many.size} new services added to OpeningHours* (Statusplattform sync)")
        assertThat(message.text).endsWith("…and 3 more")
        assertThat(metadata.eventPayload["count"]).isEqualTo(many.size)
        assertThat(metadata.eventPayload["services"] as List<*>).hasSize(NewServiceSlackNotifier.MAX_LISTED)
    }
}
