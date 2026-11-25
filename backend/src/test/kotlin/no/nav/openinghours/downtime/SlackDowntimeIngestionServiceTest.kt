package no.nav.openinghours.downtime

import jakarta.persistence.EntityManager
import no.nav.openinghours.llm.LlmException
import no.nav.openinghours.model.db.ActivityRepository
import no.nav.openinghours.model.db.ActivitySource
import no.nav.openinghours.model.db.Service
import no.nav.openinghours.model.db.ServiceRepository
import no.nav.openinghours.model.db.ServiceType
import no.nav.openinghours.model.db.SlackDowntimeMessageRepository
import no.nav.openinghours.model.db.SlackDowntimeMessageStatus
import no.nav.openinghours.service.ActivityInput
import no.nav.openinghours.service.ActivityService
import no.nav.openinghours.service.ServiceService
import no.nav.openinghours.slack.SlackApiException
import no.nav.openinghours.slack.SlackClient
import no.nav.openinghours.slack.SlackHistoryMessage
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.ValueSource
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
import org.springframework.transaction.PlatformTransactionManager
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
    @Autowired lateinit var entityManager: EntityManager
    @Autowired lateinit var serviceRepo: ServiceRepository
    @Autowired lateinit var followUps: DowntimeFollowUpHandler
    @Autowired lateinit var transactionManager: PlatformTransactionManager

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
        assertThat(ingestion.store("CDOWN", listOf(SlackHistoryMessage(ts = ts, text = text)))).isEqualTo(1)
        return repo.findAll().single { it.messageTs == ts }.id
    }

    private fun activity(ids: List<String> = listOf(serviceId.toString()), start: String? = "${future}T20:00", end: String? = "${future}T22:00") =
        ExtractedActivity(ids.map { ExtractedService(null, it) }, "Gosys nede", "Planlagt oppgradering", start, end)

    @Test
    fun `reads every configured channel and a failing channel does not block the others`() {
        val multi = SlackDowntimeIngestionService(
            slack, extractor, repo, serviceRepo, activities, activityRepo, followUps, transactionManager, clock,
            channelIds = " CDOWN , C0CMBT3K58Q,CDOWN,", lookbackHours = 24, maxAttempts = 3, batchSize = 20,
            followUpWindowHours = 24, onlyShowForNavEmployees = true
        )
        assertThat(multi.channels).containsExactly("CDOWN", "C0CMBT3K58Q")
        val ts = ts()
        `when`(slack.fetchChannelHistory(anyString(), anyString())).thenAnswer {
            if (it.getArgument<String>(0) == "CDOWN") throw SlackApiException("Slack conversations.history failed: not_in_channel")
            listOf(SlackHistoryMessage(ts = ts, text = "Gosys nede i kveld"))
        }

        assertThat(multi.fetchNewMessages()).isEqualTo(1)

        assertThat(repo.findByChannelIdAndMessageTs("C0CMBT3K58Q", ts)).isNotNull()
        assertThat(repo.findByChannelIdAndMessageTs("CDOWN", ts)).isNull()
    }

    @Test
    fun `stores announcements once and skips joins and blank messages`() {
        val ts = ts()
        val messages = listOf(
            SlackHistoryMessage(ts = ts, text = "Nedetid"),
            SlackHistoryMessage(ts = ts(1), subtype = "channel_join", text = "joined"),
            SlackHistoryMessage(ts = ts(2), text = "  ")
        )

        assertThat(ingestion.store("CDOWN", messages)).isEqualTo(1)
        assertThat(ingestion.store("CDOWN", messages)).isEqualTo(0)
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
        assertThat(ingestion.store("CDOWN", listOf(SlackHistoryMessage(ts = ts, text = text, threadTs = threadTs)))).isEqualTo(1)
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

    @ParameterizedTest
    @CsvSource(
        "true, COMPLETION", "true, CANCELLATION", "true, POSTPONEMENT",
        "false, COMPLETION", "false, CANCELLATION", "false, POSTPONEMENT"
    )
    fun `thread follow-ups update every match while top-level follow-ups update only the latest`(
        threadReply: Boolean, kind: MessageKind
    ) {
        val earlierStart = now().minusHours(2)
        val laterStart = now().minusHours(1)
        val (parentId, parentTs) = announce(
            "Gosys og Arena", -600,
            activity(start = local(earlierStart), end = local(earlierStart.plusHours(4))),
            activity(ids = listOf(otherServiceId.toString()), start = local(laterStart), end = local(laterStart.plusHours(4)))
        )
        val before = repo.findById(parentId).orElseThrow().activityIds.map(activities::get).sortedBy { it.start }
        val later = future.plusDays(1)
        val entry = ExtractedActivity(
            services = if (threadReply) emptyList() else listOf(svc(), svc(otherServiceId)),
            start = if (kind == MessageKind.POSTPONEMENT) "${later}T20:00" else null,
            duration = if (kind == MessageKind.POSTPONEMENT) ExtractedDuration(1.0, "HOURS") else null
        )

        val (replyId, status) = followUp(kind, entry, threadTs = parentTs.takeIf { threadReply })

        assertThat(status).isEqualTo(SlackDowntimeMessageStatus.ACTIVITY_UPDATED)
        val expected = if (threadReply) before else listOf(before.last())
        assertThat(repo.findById(replyId).orElseThrow().activityIds).containsExactlyInAnyOrderElementsOf(expected.map { it.id })
        expected.forEach { original ->
            val updated = activities.get(original.id)
            if (kind == MessageKind.POSTPONEMENT) {
                assertThat(updated.start.toLocalDateTime()).isEqualTo(later.atTime(20, 0))
                assertThat(updated.end.toLocalDateTime()).isEqualTo(later.atTime(21, 0))
            } else {
                assertThat(updated.start).isEqualTo(original.start)
                assertThat(updated.end.toLocalDateTime()).isEqualTo(now())
            }
        }
        if (!threadReply) assertThat(activities.get(before.first().id)).isEqualTo(before.first())
    }

    @ParameterizedTest
    @EnumSource(value = MessageKind::class, names = ["CANCELLATION", "POSTPONEMENT"])
    fun `thread-wide cancellation or indefinite postponement deletes every parent activity`(kind: MessageKind) {
        val (parentId, parentTs) = announce(
            "Gosys vedlikehold", -600,
            activity(),
            activity(start = "${future.plusDays(1)}T20:00", end = "${future.plusDays(1)}T22:00")
        )
        val activityIds = repo.findById(parentId).orElseThrow().activityIds.toSet()
        assertThat(activityIds).hasSize(2)

        val (_, status) = followUp(kind, ExtractedActivity(), threadTs = parentTs)

        assertThat(status).isEqualTo(SlackDowntimeMessageStatus.ACTIVITY_CANCELLED)
        activityIds.forEach { assertThat(activityRepo.existsById(it)).isFalse() }
        assertThat(repo.findById(parentId).orElseThrow().activityIds).isEmpty()
    }

    @Test
    fun `partial thread cancellation updates all matching windows but leaves other services alone`() {
        val (parentId, parentTs) = announce(
            "Gosys og Arena vedlikehold", -600,
            activity(ids = listOf(serviceId.toString(), otherServiceId.toString())),
            activity(
                ids = listOf(serviceId.toString(), otherServiceId.toString()),
                start = "${future.plusDays(1)}T20:00", end = "${future.plusDays(1)}T22:00"
            ),
            activity(ids = listOf(otherServiceId.toString()), start = "${future.plusDays(2)}T20:00", end = "${future.plusDays(2)}T22:00")
        )
        val before = repo.findById(parentId).orElseThrow().activityIds.map(activities::get)
        val matching = before.filter { serviceId in it.serviceIds }
        val unrelated = before.single { serviceId !in it.serviceIds }

        val (replyId, status) = followUp(
            MessageKind.CANCELLATION, ExtractedActivity(listOf(svc())), threadTs = parentTs
        )

        assertThat(status).isEqualTo(SlackDowntimeMessageStatus.ACTIVITY_UPDATED)
        assertThat(matching).hasSize(2)
        matching.forEach { assertThat(activities.get(it.id).serviceIds).containsExactly(otherServiceId) }
        assertThat(activities.get(unrelated.id)).isEqualTo(unrelated)
        assertThat(repo.findById(replyId).orElseThrow().activityIds).containsExactlyInAnyOrderElementsOf(matching.map { it.id })
    }

    @ParameterizedTest
    @CsvSource(
        "DELETED, COMPLETION", "DELETED, CANCELLATION", "DELETED, POSTPONEMENT",
        "MANUAL, COMPLETION", "MANUAL, CANCELLATION", "MANUAL, POSTPONEMENT",
        "MISSING, COMPLETION", "MISSING, CANCELLATION", "MISSING, POSTPONEMENT"
    )
    fun `thread reply with no eligible parent never changes another announcement`(parentState: String, kind: MessageKind) {
        val (parentId, parentTs) = announce("Gosys i kveld", -600, activity())
        val parentActivityId = repo.findById(parentId).orElseThrow().activityIds.single()
        when (parentState) {
            "DELETED" -> activities.delete(parentActivityId)
            "MANUAL" -> activities.update(parentActivityId, ActivityInput(
                setOf(serviceId), "Manual notice", "Keep this notice", future.atTime(20, 0), future.atTime(22, 0)
            ))
            "MISSING" -> repo.deleteById(parentId)
        }
        entityManager.flush()
        entityManager.clear()
        val start = now().minusHours(1)
        val (otherId, _) = announce(
            "Different Gosys announcement", -300,
            activity(start = local(start), end = local(start.plusHours(3)))
        )
        val otherActivityId = repo.findById(otherId).orElseThrow().activityIds.single()
        val before = activities.get(otherActivityId)
        val later = future.plusDays(1)

        val (replyId, status) = followUp(
            kind,
            ExtractedActivity(listOf(svc()), start = "${later}T20:00", end = "${later}T22:00"),
            threadTs = parentTs
        )

        assertThat(status).isEqualTo(SlackDowntimeMessageStatus.NOT_RELEVANT)
        assertThat(activities.get(otherActivityId)).isEqualTo(before)
        assertThat(repo.findById(replyId).orElseThrow().activityIds).isEmpty()
        if (parentState == "MANUAL") {
            assertThat(activities.get(parentActivityId).source).isEqualTo(ActivitySource.MANUAL)
        }
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

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `cancellation uses the catalog name instead of leaving an announcement alias behind`(withExternalService: Boolean) {
        val catalogName = services.get(serviceId).name
        val targets = listOf(ExtractedService("GS", serviceId.toString())) +
            if (withExternalService) listOf(ExtractedService("ID-porten")) else emptyList()
        val (parentId, _) = announce(
            "GS vedlikehold", -600,
            ExtractedActivity(targets, start = "${future}T20:00", end = "${future}T22:00")
        )
        val activityId = repo.findById(parentId).orElseThrow().activityIds.single()
        val created = activities.get(activityId)
        assertThat(created.serviceIds).containsExactly(serviceId)
        assertThat(created.serviceNames).contains(catalogName).doesNotContain("GS")
        if (withExternalService) assertThat(created.serviceNames).containsExactlyInAnyOrder(catalogName, "ID-porten")

        val (_, status) = followUp(
            MessageKind.CANCELLATION,
            ExtractedActivity(listOf(ExtractedService(catalogName, serviceId.toString())))
        )

        if (withExternalService) {
            assertThat(status).isEqualTo(SlackDowntimeMessageStatus.ACTIVITY_UPDATED)
            val remaining = activities.get(activityId)
            assertThat(remaining.serviceIds).isEmpty()
            assertThat(remaining.serviceNames).containsExactly("ID-porten")
        } else {
            assertThat(status).isEqualTo(SlackDowntimeMessageStatus.ACTIVITY_CANCELLED)
            assertThat(activityRepo.existsById(activityId)).isFalse()
        }
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
    fun `creates an activity for services not covered by an overlapping activity`() {
        val (existingMessage, _) = announce("Gosys i kveld", -600, activity())
        val existing = repo.findById(existingMessage).orElseThrow().activityIds.single()
        val id = storeOne("Gosys og den andre tjenesten i kveld")
        extractor.next = { DowntimeExtraction(listOf(activity(ids = listOf(serviceId.toString(), otherServiceId.toString())))) }

        assertThat(ingestion.processPending()).containsExactly(SlackDowntimeMessageStatus.ACTIVITY_CREATED)

        val message = repo.findById(id).orElseThrow()
        val created = activities.get(message.activityIds.single())
        assertThat(created.serviceIds).containsExactly(otherServiceId)
        assertThat(activities.get(existing).serviceIds).containsExactly(serviceId)
        assertThat(message.reason).contains("already covered by activity $existing")
    }

    @Test
    fun `thread reply waits until its parent has been processed`() {
        val start = now().minusMinutes(30)
        val (parentId, parentTs) = storeAt("Gosys patches now", -1800)
        val (replyId, _) = storeAt("ferdig", 0, parentTs)
        extractor.failure = LlmException("temporarily unavailable")

        assertThat(ingestion.processPending()).containsExactly(SlackDowntimeMessageStatus.PENDING)
        assertThat(extractor.calls).isEqualTo(1)
        assertThat(repo.findById(replyId).orElseThrow().attempts).isZero()

        extractor.failure = null
        val responses = ArrayDeque(listOf(
            DowntimeExtraction(listOf(ExtractedActivity(listOf(svc()), "Gosys", "Patching", local(start), local(start.plusHours(3))))),
            DowntimeExtraction(listOf(ExtractedActivity()), kind = MessageKind.COMPLETION.name)
        ))
        extractor.next = { responses.removeFirst() }

        assertThat(ingestion.processPending())
            .containsExactly(SlackDowntimeMessageStatus.ACTIVITY_CREATED, SlackDowntimeMessageStatus.ACTIVITY_UPDATED)
        val activityId = repo.findById(parentId).orElseThrow().activityIds.single()
        assertThat(activities.get(activityId).end.toLocalDateTime()).isEqualTo(now())
    }

    @Test
    fun `top-level follow-up waits while an earlier announcement is pending a retry`() {
        val start = now().minusMinutes(30)
        val (announcementId, _) = storeAt("Gosys patches now", -1800)
        val (followUpId, _) = storeAt("Gosys er ferdig", 0)
        extractor.failure = LlmException("timeout")

        assertThat(ingestion.processPending()).containsExactly(SlackDowntimeMessageStatus.PENDING)
        assertThat(extractor.calls).isEqualTo(1)
        assertThat(repo.findById(followUpId).orElseThrow().status).isEqualTo(SlackDowntimeMessageStatus.PENDING)

        extractor.failure = null
        val responses = ArrayDeque(listOf(
            DowntimeExtraction(listOf(ExtractedActivity(listOf(svc()), "Gosys", "Patching", local(start), local(start.plusHours(3))))),
            DowntimeExtraction(listOf(ExtractedActivity(listOf(svc()))), kind = MessageKind.COMPLETION.name)
        ))
        extractor.next = { responses.removeFirst() }

        assertThat(ingestion.processPending())
            .containsExactly(SlackDowntimeMessageStatus.ACTIVITY_CREATED, SlackDowntimeMessageStatus.ACTIVITY_UPDATED)
        val activityId = repo.findById(announcementId).orElseThrow().activityIds.single()
        assertThat(activities.get(activityId).end.toLocalDateTime()).isEqualTo(now())
    }

    @Test
    fun `a recent thread update does not extend the follow-up window of an old announcement`() {
        val (parentId, parentTs) = announce("Gosys og annen tjeneste", -36 * 3600, activity(ids = listOf(serviceId.toString(), otherServiceId.toString())))
        val activityId = repo.findById(parentId).orElseThrow().activityIds.single()
        storeAt("den andre tjenesten er avlyst", -13 * 3600, parentTs)
        extractor.next = { DowntimeExtraction(listOf(ExtractedActivity(listOf(svc(otherServiceId)))), kind = MessageKind.CANCELLATION.name) }
        assertThat(ingestion.processPending()).doesNotContain(SlackDowntimeMessageStatus.NOT_RELEVANT, SlackDowntimeMessageStatus.FAILED)

        val (_, status) = followUp(MessageKind.CANCELLATION, ExtractedActivity(listOf(svc())))

        assertThat(status).isEqualTo(SlackDowntimeMessageStatus.NOT_RELEVANT)
        assertThat(activities.get(activityId).serviceIds).containsExactly(serviceId)
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
