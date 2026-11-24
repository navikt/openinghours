package no.nav.openinghours.slack

import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.startsWith
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.client.ExpectedCount
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule

class SlackClientHistoryTest {

    private val objectMapper = JsonMapper.builder().addModule(KotlinModule.Builder().build()).build()
    private val restTemplate = SlackConfig().slackRestTemplate(objectMapper)
    private val server = MockRestServiceServer.bindTo(restTemplate).build()
    private var now = 0L
    private val client = SlackClient(restTemplate, "http://slack.test/api/", "xoxb-test", "", SlackChannelDelivery({ now }, { now += it }))
    private val base = "http://slack.test/api/conversations.history?channel=CDOWN&oldest=1700000000.000000&inclusive=false&limit=200"

    @Test
    fun `follows pagination and returns messages oldest first`() {
        server.expect(requestTo(base))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer xoxb-test"))
            .andRespond(withSuccess(
                """{"ok":true,"messages":[{"ts":"1700000300.000200","text":"third"},{"ts":"1700000200.000100","text":"second"}],
                   "has_more":true,"response_metadata":{"next_cursor":"abc="}}""",
                MediaType.APPLICATION_JSON
            ))
        server.expect(requestTo("$base&cursor=abc%3D"))
            .andRespond(withSuccess(
                """{"ok":true,"messages":[{"ts":"1700000100.000000","subtype":"bot_message","text":"",
                   "attachments":[{"title":"Nedetid","text":"Gosys nede"}]}],"has_more":false}""",
                MediaType.APPLICATION_JSON
            ))

        val messages = client.fetchChannelHistory("CDOWN", "1700000000.000000")

        assertThat(messages.map { it.ts }).containsExactly("1700000100.000000", "1700000200.000100", "1700000300.000200")
        assertThat(messages.first().fullText()).isEqualTo("Nedetid\nGosys nede")
        server.verify()
    }

    @Test
    fun `fetches thread replies without the parent message`() {
        server.expect(requestTo("http://slack.test/api/conversations.replies?channel=CDOWN&ts=1700000000.000100&oldest=1700000000.000100&inclusive=false&limit=200"))
            .andRespond(withSuccess(
                """{"ok":true,"messages":[{"ts":"1700000000.000100","text":"parent"},
                   {"ts":"1700000500.000000","thread_ts":"1700000000.000100","text":"ferdig"}],"has_more":false}""",
                MediaType.APPLICATION_JSON
            ))

        val replies = client.fetchThreadReplies("CDOWN", "1700000000.000100", "1700000000.000100")

        assertThat(replies.map { it.text }).containsExactly("ferdig")
        assertThat(replies.single().threadTs).isEqualTo("1700000000.000100")
        server.verify()
    }

    @Test
    fun `throws instead of returning a partial batch when the page limit is reached`() {
        server.expect(ExpectedCount.times(SlackClient.MAX_HISTORY_PAGES), requestTo(startsWith(base)))
            .andRespond(withSuccess(
                """{"ok":true,"messages":[{"ts":"1700000300.000200","text":"newest"}],
                   "has_more":true,"response_metadata":{"next_cursor":"more="}}""",
                MediaType.APPLICATION_JSON
            ))

        val e = assertThrows<SlackApiException> { client.fetchChannelHistory("CDOWN", "1700000000.000000") }
        assertThat(e.message).contains("partial batch")
        server.verify()
    }

    @Test
    fun `throws on Slack API error`() {
        server.expect(requestTo(base))
            .andRespond(withSuccess("""{"ok":false,"error":"not_in_channel"}""", MediaType.APPLICATION_JSON))

        val e = assertThrows<SlackApiException> { client.fetchChannelHistory("CDOWN", "1700000000.000000") }
        assertThat(e.message).contains("not_in_channel")
    }
}
