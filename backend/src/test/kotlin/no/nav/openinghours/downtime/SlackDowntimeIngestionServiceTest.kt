package no.nav.openinghours.downtime

import no.nav.openinghours.llm.LlmException
import no.nav.openinghours.model.db.ActivityRepository
import no.nav.openinghours.model.db.ActivitySource
import no.nav.openinghours.model.db.Service
import no.nav.openinghours.model.db.ServiceType
import no.nav.openinghours.model.db.SlackDowntimeMessageRepository
import no.nav.openinghours.model.db.SlackDowntimeMessageStatus
import no.nav.openinghours.service.ActivityInput
import no.nav.openinghours.service.ActivityService
import no.nav.openinghours.service.ServiceService
import no.nav.openinghours.slack.SlackClient
import no.nav.openinghours.slack.SlackHistoryMessage
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.http.HttpStatus
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID

class FakeDowntimeExtractor : DowntimeExtractor {
    override val enabled = true
    var next: (List<Service>) -> DowntimeExtraction = { DowntimeExtraction() }
    var failure: Exception? = null
    var calls = 0
    var lastParentText: String? = null

    override fun extract(text: String, postedAt: Instant, services: List<Service>, parentText: String?): DowntimeExtractionResult {
        calls++
        lastParentText = parentText
        failure?.let { throw it }
        return DowntimeExtractionResult(next(services), """{"fake":true}""")
    }
}

@SpringBootTest
@Testcontainers
@Transactional
@AutoConfigureMockMvc(addFilters = false)
class SlackDowntimeIngestionServiceTest {

    companion object {
        @Container
        val postgres = PostgreSQLContainer("postgres:15")

        @JvmStatic
        @DynamicPropertySource
        @Suppress("unused")
        fun overrideProps(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
            registry.add("slack.downtime.channel-id") { "CDOWN" }
        }
    }

    @TestConfiguration
    class Config {
        @Bean
        @Primary
        fun fakeDowntimeExtractor() = FakeDowntimeExtractor()
    }

    @Autowired lateinit var ingestion: SlackDowntimeIngestionService
    @Autowired lateinit var extractor: FakeDowntimeExtractor
    @Autowired lateinit var repo: SlackDowntimeMessageRepository
    @Autowired lateinit var services: ServiceService
    @Autowired lateinit var activities: ActivityService
    @Autowired lateinit var activityRepo: ActivityRepository
    @Autowired lateinit var clock: Clock
    @Autowired lateinit var mockMvc: MockMvc

    @MockitoBean
    lateinit var slack: SlackClient

    private lateinit var serviceId: UUID
    private lateinit var otherServiceId: UUID
    private val future = LocalDate.now().plusYears(1)

    @BeforeEach
    fun setup() {
        serviceId = services.save("downtime-svc-${UUID.randomUUID()}", ServiceType.TJENESTE, "team").id
        otherServiceId = services.save("downtime-other-${UUID.randomUUID()}", ServiceType.TJENESTE, "team").id
        extractor.failure = null
        extractor.calls = 0
        extractor.next = { DowntimeExtraction() }
    }

    private fun ts(offsetSeconds: Long = 0) =
        SlackDowntimeIngestionService.instantToTs(Instant.now().plusSeconds(offsetSeconds))

    private fun storeOne(text: String = "Gosys nede i kveld"): UUID {
        val ts = ts()
        assertThat(ingestion.store(listOf(SlackHistoryMessage(ts = ts, text = text)))).isEqualTo(1)
        return repo.findAll().single { it.messageTs == ts }.id
    }

    private fun activity(ids: List<String> = listOf(serviceId.toString()), start: String? = "${future}T20:00", end: String? = "${future}T22:00") =
        ExtractedActivity(ids.map { ExtractedService(null, it) }, "Gosys nede", "Planlagt oppgradering", start, end)

    @Test
    fun `stores announcements once and skips joins and blank messages`() {
        val ts = ts()
        val messages = listOf(
            SlackHistoryMessage(ts = ts, text = "Nedetid"),
            SlackHistoryMessage(ts = ts(1), subtype = "channel_join", text = "joined"),
            SlackHistoryMessage(ts = ts(2), text = "  ")
        )

        assertThat(ingestion.store(messages)).isEqualTo(1)
        assertThat(ingestion.store(messages)).isEqualTo(0)
        val stored = repo.findAll().single { it.messageTs == ts }
        assertThat(stored.status).isEqualTo(SlackDowntimeMessageStatus.PENDING)
        assertThat(stored.postedAt).isEqualTo(SlackDowntimeIngestionService.tsToInstant(ts))
    }

    @Test
    fun `creates an activity from the extraction and links it to the message`() {
        val id = storeOne()
        extractor.next = { DowntimeExtraction(listOf(activity()), "maintenance") }

        assertThat(ingestion.processPending()).containsExactly(SlackDowntimeMessageStatus.ACTIVITY_CREATED)

        val message = repo.findById(id).orElseThrow()
        assertThat(message.extraction).isEqualTo("""{"fake":true}""")
        assertThat(message.attempts).isEqualTo(1)
        val created = activities.get(message.activityIds.single())
        assertThat(created.serviceIds).containsExactly(serviceId)
        assertThat(created.header).isEqualTo("Gosys nede")
        assertThat(created.onlyShowForNavEmployees).isTrue()
        assertThat(created.start.toLocalDateTime().toString()).isEqualTo("${future}T20:00")
        assertThat(ingestion.processPending()).isEmpty()
    }

    @Test
    fun `poll endpoint imports history and thread follow-ups and advances both cursors`() {
        val parentTs = ts(-600)
        val replyTs = ts()
        val start = now().minusMinutes(10)
        `when`(slack.hasToken).thenReturn(true)
        `when`(slack.fetchChannelHistory(anyString(), anyString()))
            .thenReturn(listOf(SlackHistoryMessage(ts = parentTs, text = "Gosys nede")), emptyList())
        `when`(slack.fetchThreadReplies("CDOWN", parentTs, parentTs))
            .thenReturn(emptyList(), listOf(SlackHistoryMessage(ts = replyTs, threadTs = parentTs, text = "Ferdig")))
        `when`(slack.fetchThreadReplies("CDOWN", parentTs, replyTs)).thenReturn(emptyList())
        extractor.next = {
            DowntimeExtraction(listOf(activity(start = local(start), end = local(start.plusHours(1)))))
        }

        mockMvc.post("/api/openinghours/slack-downtime/poll").andExpect {
            status { isOk() }
            jsonPath("$.fetched") { value(1) }
            jsonPath("$.activityCreated") { value(1) }
        }
        val parent = repo.findByChannelIdAndMessageTs("CDOWN", parentTs)!!
        val activityId = parent.activityIds.single()

        mockMvc.get("/api/openinghours/slack-downtime/messages?limit=1").andExpect {
            status { isOk() }
            jsonPath("$[0].messageTs") { value(parentTs) }
            jsonPath("$[0].status") { value("ACTIVITY_CREATED") }
            jsonPath("$[0].activityIds[0]") { value(activityId.toString()) }
        }

        extractor.next = { DowntimeExtraction(kind = "COMPLETION") }
        mockMvc.post("/api/openinghours/slack-downtime/poll").andExpect {
            status { isOk() }
            jsonPath("$.fetched") { value(1) }
            jsonPath("$.activityUpdated") { value(1) }
        }
        assertThat(activities.get(activityId).end.toLocalDateTime()).isEqualTo(now())
        assertThat(extractor.lastParentText).isEqualTo("Gosys nede")

        mockMvc.post("/api/openinghours/slack-downtime/poll").andExpect {
            status { isOk() }
            jsonPath("$.fetched") { value(0) }
            jsonPath("$.activityUpdated") { value(0) }
        }
        verify(slack, times(2)).fetchChannelHistory("CDOWN", parentTs)
        verify(slack).fetchThreadReplies("CDOWN", parentTs, replyTs)
        assertThat(extractor.calls).isEqualTo(2)
    }

    @Test
    fun `poll endpoint reports unavailable when ingestion is disabled`() {
        mockMvc.post("/api/openinghours/slack-downtime/poll").andExpect {
            status { isServiceUnavailable() }
        }
        verify(slack, never()).fetchChannelHistory(anyString(), anyString())
    }

    @Test
    fun `retry endpoint requeues failures and rejects applied or missing messages`() {
        val id = storeOne()
        extractor.failure = LlmException("boom")
        repeat(3) { ingestion.processPending() }

        mockMvc.post("/api/openinghours/slack-downtime/messages/$id/retry").andExpect {
            status { isOk() }
            jsonPath("$.status") { value("PENDING") }
            jsonPath("$.attempts") { value(0) }
        }
        extractor.failure = null
        extractor.next = { DowntimeExtraction(listOf(activity())) }
        ingestion.processPending()

        mockMvc.post("/api/openinghours/slack-downtime/messages/$id/retry").andExpect {
            status { isConflict() }
        }
        mockMvc.post("/api/openinghours/slack-downtime/messages/${UUID.randomUUID()}/retry").andExpect {
            status { isNotFound() }
        }
    }

    @Test
    fun `marks message not relevant when the model references unknown services or invalid times`() {
        val id = storeOne()
        extractor.next = {
            DowntimeExtraction(listOf(
                activity(ids = listOf(UUID.randomUUID().toString(), "not-a-uuid")),
                activity(start = "${future}T20:00", end = "${future.plusDays(20)}T19:00"),
                activity(start = "tonight")
            ))
        }

        assertThat(ingestion.processPending()).containsExactly(SlackDowntimeMessageStatus.NOT_RELEVANT)

        val message = repo.findById(id).orElseThrow()
        assertThat(message.activityIds).isEmpty()
        assertThat(message.reason).contains("unknown serviceId", "no services", "longer than 14 days", "invalid start")
    }

    @Test
    fun `keeps activities that have already ended`() {
        val id = storeOne()
        val past = LocalDate.now().minusDays(2)
        extractor.next = { DowntimeExtraction(listOf(activity(start = "${past}T10:00", end = "${past}T10:01"))) }

        assertThat(ingestion.processPending()).containsExactly(SlackDowntimeMessageStatus.ACTIVITY_CREATED)
        assertThat(repo.findById(id).orElseThrow().activityIds).hasSize(1)
    }

    private fun now(): LocalDateTime = LocalDateTime.now(clock).truncatedTo(ChronoUnit.MINUTES)

    private fun local(t: LocalDateTime) = t.toString().take(16)

    private fun storeAt(text: String, offsetSeconds: Long, threadTs: String? = null): Pair<UUID, String> {
        val ts = ts(offsetSeconds)
        assertThat(ingestion.store(listOf(SlackHistoryMessage(ts = ts, text = text, threadTs = threadTs)))).isEqualTo(1)
        return repo.findAll().single { it.messageTs == ts }.id to ts
    }

    private fun announce(text: String, offsetSeconds: Long, vararg entries: ExtractedActivity): Pair<UUID, String> {
        val stored = storeAt(text, offsetSeconds)
        extractor.next = { DowntimeExtraction(entries.toList()) }
        assertThat(ingestion.processPending()).containsExactly(SlackDowntimeMessageStatus.ACTIVITY_CREATED)
        return stored
    }

    private fun followUp(kind: MessageKind, vararg entries: ExtractedActivity, threadTs: String? = null): Pair<UUID, SlackDowntimeMessageStatus> {
        val (id, _) = storeAt("follow-up", 0, threadTs)
        extractor.next = { DowntimeExtraction(entries.toList(), kind = kind.name) }
        return id to ingestion.processPending().single()
    }

    private fun svc(id: UUID = serviceId) = ExtractedService(null, id.toString())

    @Test
    fun `thread reply completion sets the end of the parent activity`() {
        val start = now().minusMinutes(30)
        val (parentId, parentTs) = announce(
            "Gosys patches now", -1800,
            ExtractedActivity(listOf(svc()), "Gosys", "Patching", local(start), local(start.plusHours(3)))
        )
        val activityId = repo.findById(parentId).orElseThrow().activityIds.single()

        val (replyId, status) = followUp(MessageKind.COMPLETION, ExtractedActivity(), threadTs = parentTs)

        assertThat(status).isEqualTo(SlackDowntimeMessageStatus.ACTIVITY_UPDATED)
        assertThat(extractor.lastParentText).isEqualTo("Gosys patches now")
        assertThat(activities.get(activityId).end.toLocalDateTime()).isEqualTo(now())
        val reply = repo.findById(replyId).orElseThrow()
        assertThat(reply.threadTs).isEqualTo(parentTs)
        assertThat(reply.activityIds).containsExactly(activityId)
        assertThat(reply.reason).contains("completed")
    }

    @Test
    fun `cancellation before start deletes the activity and records it on the announcement`() {
        val (parentId, _) = announce("Gosys i kveld", -600, activity())
        val activityId = repo.findById(parentId).orElseThrow().activityIds.single()

        val (_, status) = followUp(MessageKind.CANCELLATION, ExtractedActivity(listOf(svc())))

        assertThat(status).isEqualTo(SlackDowntimeMessageStatus.ACTIVITY_CANCELLED)
        assertThat(activityRepo.existsById(activityId)).isFalse()
        val parent = repo.findById(parentId).orElseThrow()
        assertThat(parent.activityIds).isEmpty()
        assertThat(parent.reason).contains("deleted: cancelled before start")
    }

    @Test
    fun `cancellation of a started activity ends it now`() {
        val start = now().minusHours(1)
        val (parentId, _) = announce(
            "Gosys nede", -3600,
            ExtractedActivity(listOf(svc()), "Gosys", "Oppgradering", local(start), local(start.plusHours(4)))
        )
        val activityId = repo.findById(parentId).orElseThrow().activityIds.single()

        val (_, status) = followUp(MessageKind.CANCELLATION, ExtractedActivity(listOf(svc())))

        assertThat(status).isEqualTo(SlackDowntimeMessageStatus.ACTIVITY_UPDATED)
        assertThat(activities.get(activityId).end.toLocalDateTime()).isEqualTo(now())
    }

    @Test
    fun `partial cancellation removes only the named services`() {
        val (parentId, _) = announce(
            "Gosys og Arena i kveld", -600,
            ExtractedActivity(listOf(svc(), svc(otherServiceId)), "Patching", "Patching", "${future}T20:00", "${future}T22:00")
        )
        val activityId = repo.findById(parentId).orElseThrow().activityIds.single()

        val (_, status) = followUp(MessageKind.CANCELLATION, ExtractedActivity(listOf(svc(otherServiceId))))

        assertThat(status).isEqualTo(SlackDowntimeMessageStatus.ACTIVITY_UPDATED)
        assertThat(activities.get(activityId).serviceIds).containsExactly(serviceId)
    }

    @Test
    fun `postponement moves the activity and without a new time deletes it`() {
        val (parentId, _) = announce("Gosys i kveld", -600, activity())
        val activityId = repo.findById(parentId).orElseThrow().activityIds.single()
        val later = future.plusDays(1)

        val (_, moved) = followUp(
            MessageKind.POSTPONEMENT,
            ExtractedActivity(listOf(svc()), start = "${later}T20:00", duration = ExtractedDuration(1.0, "HOURS"))
        )
        assertThat(moved).isEqualTo(SlackDowntimeMessageStatus.ACTIVITY_UPDATED)
        val view = activities.get(activityId)
        assertThat(view.start.toLocalDateTime()).isEqualTo(later.atTime(20, 0))
        assertThat(view.end.toLocalDateTime()).isEqualTo(later.atTime(21, 0))

        val (_, dropped) = followUp(MessageKind.POSTPONEMENT, ExtractedActivity(listOf(svc())))
        assertThat(dropped).isEqualTo(SlackDowntimeMessageStatus.ACTIVITY_CANCELLED)
        assertThat(activityRepo.existsById(activityId)).isFalse()
    }

    @Test
    fun `follow-ups never change manual activities`() {
        val manual = activities.create(ActivityInput(
            setOf(serviceId), "Manuell", "Satt inn for hånd", future.atTime(20, 0), future.atTime(22, 0)
        ))

        val (id, status) = followUp(MessageKind.CANCELLATION, ExtractedActivity(listOf(svc())))

        assertThat(status).isEqualTo(SlackDowntimeMessageStatus.NOT_RELEVANT)
        assertThat(activities.get(manual.id).source).isEqualTo(ActivitySource.MANUAL)
        assertThat(repo.findById(id).orElseThrow().reason).contains("without matching activity")
    }

    @Test
    fun `follow-up without services outside a thread is not applied`() {
        announce("Gosys i kveld", -600, activity())

        val (id, status) = followUp(MessageKind.COMPLETION, ExtractedActivity())

        assertThat(status).isEqualTo(SlackDowntimeMessageStatus.NOT_RELEVANT)
        assertThat(repo.findById(id).orElseThrow().reason).contains("no thread parent")
    }

    @Test
    fun `skips activities that duplicate an existing one`() {
        announce("Gosys i kveld", -600, activity())
        val id = storeOne("Påminnelse: Gosys i kveld")
        extractor.next = { DowntimeExtraction(listOf(activity(start = "${future}T20:30", end = "${future}T21:00"))) }

        assertThat(ingestion.processPending()).containsExactly(SlackDowntimeMessageStatus.NOT_RELEVANT)
        assertThat(repo.findById(id).orElseThrow().reason).contains("duplicate of activity")
    }

    @Test
    fun `stores services outside the catalog by name and resolves times by the rules`() {
        val (id, _) = announce(
            "ID-porten vedlikehold", 0,
            ExtractedActivity(
                listOf(ExtractedService("ID-porten", null), ExtractedService("Maskinporten", "not-a-uuid")),
                "ID-porten og Maskinporten", "Kortvarig nedetid <@U123>", start = "${future}T16:00", short = true
            )
        )

        val message = repo.findById(id).orElseThrow()
        val created = activities.get(message.activityIds.single())
        assertThat(created.serviceIds).isEmpty()
        assertThat(created.serviceNames).containsExactlyInAnyOrder("ID-porten", "Maskinporten")
        assertThat(created.source).isEqualTo(ActivitySource.SLACK)
        assertThat(created.message).isEqualTo("Kortvarig nedetid")
        assertThat(created.end.toLocalDateTime()).isEqualTo(future.atTime(16, 30))
        assertThat(message.reason).contains("assumed 30 minutes")
    }

    @Test
    fun `retries extraction failures and gives up after max attempts`() {
        val id = storeOne()
        extractor.failure = LlmException("boom")

        assertThat(ingestion.processPending()).containsExactly(SlackDowntimeMessageStatus.PENDING)
        assertThat(ingestion.processPending()).containsExactly(SlackDowntimeMessageStatus.PENDING)
        assertThat(ingestion.processPending()).containsExactly(SlackDowntimeMessageStatus.FAILED)
        assertThat(ingestion.processPending()).isEmpty()
        assertThat(repo.findById(id).orElseThrow().reason).contains("boom")

        extractor.failure = null
        extractor.next = { DowntimeExtraction(listOf(activity())) }
        assertThat(ingestion.retry(id).status).isEqualTo(SlackDowntimeMessageStatus.PENDING)
        assertThat(ingestion.processPending()).containsExactly(SlackDowntimeMessageStatus.ACTIVITY_CREATED)

        val e = assertThrows<ResponseStatusException> { ingestion.retry(id) }
        assertThat(e.statusCode).isEqualTo(HttpStatus.CONFLICT)
    }

    @Test
    fun `lists stored messages newest first`() {
        val first = storeOne("first")
        val second = storeOne("second")

        assertThat(ingestion.list(10).map { it.id }).containsSubsequence(second, first)
    }

    @Test
    fun `converts between Slack timestamps and instants`() {
        val instant = Instant.parse("2030-06-10T08:00:00.123456Z")
        assertThat(SlackDowntimeIngestionService.instantToTs(instant)).isEqualTo("1907308800.123456")
        assertThat(SlackDowntimeIngestionService.tsToInstant("1907308800.123456")).isEqualTo(instant)
    }
}
