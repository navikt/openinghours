package no.nav.openinghours.slack

import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule
import no.nav.openinghours.model.db.ServiceType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.ExpectedCount.never
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import java.util.UUID

class NewServiceSlackNotifierTest {

    private val objectMapper = JsonMapper.builder().addModule(KotlinModule.Builder().build()).build()
    private val restTemplate = SlackConfig().slackRestTemplate(objectMapper)
    private val server = MockRestServiceServer.bindTo(restTemplate).build()
    private var now = 0L
    private val delivery = SlackChannelDelivery({ now }, { now += it })

    private fun notifier(token: String = "xoxb-test", channel: String = "C123") =
        NewServiceSlackNotifier(SlackClient(restTemplate, "http://slack.test/api/", token, channel, delivery), objectMapper)

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

        val client = SlackClient(restTemplate, "http://slack.test/api", "xoxb-test", "C123", delivery)
        val e = assertThrows<SlackApiException> { client.postMessage(SlackMessage("hi")) }
        assertThat(e.message).contains("not_in_channel")
    }

    @Test
    fun `splits large events without dropping services`() {
        val many = (1..53).map {
            AddedService(UUID.randomUUID(), "svc-$it", ServiceType.KOMPONENT, "team")
        }

        assertSizeSafeDelivery(many)
    }

    @Test
    fun `splits metadata using UTF-8 bytes including escaped names and teams`() {
        val many = (1..12).map {
            AddedService(UUID.randomUUID(), "\u754c".repeat(100), ServiceType.TJENESTE, "\"\\\n".repeat(33))
        }
        assertSizeSafeDelivery(many)
    }

    @Test
    fun `allows exactly the metadata limit and splits one byte over it`() {
        val services = listOf(
            AddedService(UUID.randomUUID(), "", ServiceType.TJENESTE, "team"),
            AddedService(UUID.randomUUID(), "", ServiceType.TJENESTE, "team")
        )
        val source = ServiceAddedSource.STATUSPLATTFORM_SYNC
        val initial = notifier().buildMessages(ServicesAddedEvent(services, source)).single()
        val padding = NewServiceSlackNotifier.MAX_METADATA_BYTES -
            objectMapper.writeValueAsBytes(initial.metadata).size
        val atLimit = listOf(services[0].copy(name = "x".repeat(padding)), services[1])
        val message = notifier().buildMessages(ServicesAddedEvent(atLimit, source)).single()

        assertThat(objectMapper.writeValueAsBytes(message.metadata)).hasSize(NewServiceSlackNotifier.MAX_METADATA_BYTES)
        val overLimit = listOf(atLimit[0].copy(name = atLimit[0].name + "x"), atLimit[1])
        assertThat(notifier().buildMessages(ServicesAddedEvent(overLimit, source))).hasSize(2)
        assertSizeSafeDelivery(overLimit)
    }

    @Test
    fun `rejects a service that cannot fit in one metadata payload`() {
        val oversized = event.copy(services = listOf(event.services.single().copy(name = "x".repeat(2000))))
        assertThrows<IllegalArgumentException> { notifier().buildMessages(oversized) }
    }

    @Test
    fun `retries the same batch using Retry-After before sending remaining batches`() {
        val services = (1..30).map {
            AddedService(UUID.randomUUID(), "svc-$it", ServiceType.TJENESTE, "team")
        }
        val added = ServicesAddedEvent(services, ServiceAddedSource.STATUSPLATTFORM_SYNC)
        val messages = notifier().buildMessages(added)
        assertThat(messages.size).isGreaterThan(1)
        val firstMetadata = objectMapper.valueToTree<JsonNode>(messages.first().metadata)
        repeat(2) { attempt ->
            server.expect(requestTo("http://slack.test/api/chat.postMessage"))
                .andExpect { request ->
                    val body = (request as org.springframework.mock.http.client.MockClientHttpRequest).bodyAsString
                    assertThat(objectMapper.readTree(body).get("metadata")).isEqualTo(firstMetadata)
                    assertThat(now).isEqualTo(if (attempt == 0) 0L else 3000L)
                }
                .andRespond(
                    if (attempt == 0) withStatus(HttpStatus.TOO_MANY_REQUESTS).header("Retry-After", "3")
                    else withSuccess("""{"ok":true}""", MediaType.APPLICATION_JSON)
                )
        }
        messages.drop(1).forEachIndexed { index, message ->
            server.expect(requestTo("http://slack.test/api/chat.postMessage"))
                .andExpect { request ->
                    val body = (request as org.springframework.mock.http.client.MockClientHttpRequest).bodyAsString
                    assertThat(objectMapper.readTree(body).get("metadata"))
                        .isEqualTo(objectMapper.valueToTree<JsonNode>(message.metadata))
                    assertThat(now).isEqualTo(4000L + index * 1000L)
                }
                .andRespond(withSuccess("""{"ok":true}""", MediaType.APPLICATION_JSON))
        }

        notifier().onServicesAdded(added)
        server.verify()
    }

    private fun assertSizeSafeDelivery(services: List<AddedService>) {
        val source = ServiceAddedSource.STATUSPLATTFORM_SYNC
        val messages = notifier().buildMessages(ServicesAddedEvent(services, source))
        assertThat(messages.size).isGreaterThan(1)
        val delivered = mutableListOf<String>()
        messages.forEach {
            server.expect(requestTo("http://slack.test/api/chat.postMessage"))
                .andExpect { request ->
                    val body = (request as org.springframework.mock.http.client.MockClientHttpRequest).bodyAsString
                    val metadata = objectMapper.readTree(body).get("metadata")
                    assertThat(objectMapper.writeValueAsBytes(metadata).size)
                        .isLessThanOrEqualTo(NewServiceSlackNotifier.MAX_METADATA_BYTES)
                    val batch = metadata.get("event_payload").get("services")
                    assertThat(metadata.get("event_payload").get("count").asInt()).isEqualTo(batch.size())
                    batch.forEach { service ->
                        delivered += service.get("id").asText()
                        val original = services.single { it.id.toString() == service.get("id").asText() }
                        assertThat(service.get("name").asText()).isEqualTo(original.name)
                        assertThat(service.get("team").asText()).isEqualTo(original.team)
                    }
                }
                .andRespond(withSuccess("""{"ok":true}""", MediaType.APPLICATION_JSON))
        }
        notifier().onServicesAdded(ServicesAddedEvent(services, source))
        assertThat(delivered).containsExactlyElementsOf(services.map { it.id.toString() })
        server.verify()
    }
}
