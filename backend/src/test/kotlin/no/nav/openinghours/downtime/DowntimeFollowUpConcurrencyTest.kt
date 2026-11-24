package no.nav.openinghours.downtime

import no.nav.openinghours.model.db.ActivityRepository
import no.nav.openinghours.model.db.ActivitySource
import no.nav.openinghours.model.db.SlackDowntimeMessage
import no.nav.openinghours.model.db.SlackDowntimeMessageRepository
import no.nav.openinghours.model.db.SlackDowntimeMessageStatus
import no.nav.openinghours.service.ActivityInput
import no.nav.openinghours.service.ActivityService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import java.time.Clock
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@SpringBootTest
@Testcontainers
class DowntimeFollowUpConcurrencyTest {
    companion object {
        @Container
        val postgres = PostgreSQLContainer("postgres:15")

        @JvmStatic
        @DynamicPropertySource
        fun overrideProps(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
        }
    }

    @Autowired lateinit var handler: DowntimeFollowUpHandler
    @Autowired lateinit var activities: ActivityService
    @Autowired lateinit var activityRepo: ActivityRepository
    @Autowired lateinit var messageRepo: SlackDowntimeMessageRepository
    @Autowired lateinit var transactionManager: PlatformTransactionManager
    @Autowired lateinit var jdbc: JdbcTemplate
    @Autowired lateinit var clock: Clock

    private data class Fixture(val activityId: UUID, val parentId: UUID, val replyId: UUID, val input: ActivityInput)

    private fun fixture(threadReply: Boolean): Fixture = TransactionTemplate(transactionManager).execute {
        val posted = clock.instant().truncatedTo(ChronoUnit.MINUTES)
        val start = LocalDateTime.ofInstant(posted, clock.zone).minusHours(1)
        val input = ActivityInput(
            emptySet(), "Gosys maintenance", "Patching", start, start.plusHours(3),
            serviceNames = setOf("Gosys"), source = ActivitySource.SLACK
        )
        val activity = activities.create(input)
        val parentTs = SlackDowntimeIngestionService.instantToTs(posted.minusSeconds(3600))
        val parent = messageRepo.saveAndFlush(SlackDowntimeMessage(
            channelId = "C-${UUID.randomUUID()}", messageTs = parentTs,
            postedAt = posted.minusSeconds(3600), rawText = "Gosys maintenance",
            status = SlackDowntimeMessageStatus.ACTIVITY_CREATED, activityIds = mutableSetOf(activity.id)
        ))
        val reply = messageRepo.saveAndFlush(SlackDowntimeMessage(
            channelId = parent.channelId, messageTs = SlackDowntimeIngestionService.instantToTs(posted),
            threadTs = parentTs.takeIf { threadReply }, postedAt = posted, rawText = "Gosys ferdig"
        ))
        Fixture(activity.id, parent.id, reply.id, input)
    }!!

    private fun apply(fixture: Fixture, threadReply: Boolean, kind: MessageKind): DowntimeOutcome = handler.apply(
        kind,
        messageRepo.findById(fixture.replyId).orElseThrow(),
        if (threadReply) messageRepo.findById(fixture.parentId).orElseThrow() else null,
        DowntimeExtraction(listOf(ExtractedActivity(
            services = if (threadReply) emptyList() else listOf(ExtractedService("Gosys"))
        )), kind = kind.name),
        emptyMap()
    )

    private fun awaitBlocked(pid: Int): Boolean {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (System.nanoTime() < deadline) {
            if (jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM pg_stat_activity WHERE pid = ? AND wait_event_type = 'Lock')",
                Boolean::class.java, pid
            ) == true) return true
            Thread.sleep(10)
        }
        return false
    }

    private fun cleanUp(fixture: Fixture) {
        TransactionTemplate(transactionManager).execute {
            messageRepo.deleteAllById(listOf(fixture.replyId, fixture.parentId))
            activities.delete(fixture.activityId)
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `rechecks stale source after waiting for a manual edit to commit`(threadReply: Boolean) {
        val fixture = fixture(threadReply)
        val executor = Executors.newFixedThreadPool(2)
        val manualLocked = CountDownLatch(1)
        val releaseManual = CountDownLatch(1)
        val candidateRead = CountDownLatch(1)
        val followUpPid = AtomicInteger()
        val edited = fixture.input.copy(
            header = "Manual edit", message = "Keep this notice", end = fixture.input.end.plusHours(1),
            serviceNames = setOf("ID-porten")
        )
        try {
            val manual = executor.submit {
                TransactionTemplate(transactionManager).execute {
                    jdbc.queryForObject(
                        "SELECT id FROM activity WHERE id = ? FOR UPDATE", UUID::class.java, fixture.activityId
                    )
                    manualLocked.countDown()
                    check(releaseManual.await(10, TimeUnit.SECONDS))
                    activities.update(fixture.activityId, edited)
                }
            }
            assertThat(manualLocked.await(5, TimeUnit.SECONDS)).isTrue()
            val followUp = executor.submit<DowntimeOutcome> {
                TransactionTemplate(transactionManager).execute {
                    followUpPid.set(jdbc.queryForObject("SELECT pg_backend_pid()", Int::class.java)!!)
                    val cached = activityRepo.findById(fixture.activityId).orElseThrow()
                    assertThat(cached.source).isEqualTo(ActivitySource.SLACK)
                    candidateRead.countDown()
                    apply(fixture, threadReply, MessageKind.POSTPONEMENT)
                }!!
            }
            assertThat(candidateRead.await(5, TimeUnit.SECONDS)).isTrue()
            assertThat(awaitBlocked(followUpPid.get())).isTrue()
            releaseManual.countDown()

            manual.get(10, TimeUnit.SECONDS)
            assertThat(followUp.get(10, TimeUnit.SECONDS).status).isEqualTo(SlackDowntimeMessageStatus.NOT_RELEVANT)
            val saved = activities.get(fixture.activityId)
            assertThat(saved.source).isEqualTo(ActivitySource.MANUAL)
            assertThat(saved.header).isEqualTo(edited.header)
            assertThat(saved.message).isEqualTo(edited.message)
            assertThat(saved.end.toLocalDateTime()).isEqualTo(edited.end)
            assertThat(saved.serviceNames).containsExactly("ID-porten")
        } finally {
            releaseManual.countDown()
            executor.shutdown()
            check(executor.awaitTermination(15, TimeUnit.SECONDS))
            cleanUp(fixture)
        }
    }

    @Test
    fun `holds activity lock until follow-up commits so a later manual edit wins`() {
        val fixture = fixture(threadReply = true)
        val executor = Executors.newFixedThreadPool(2)
        val followUpApplied = CountDownLatch(1)
        val releaseFollowUp = CountDownLatch(1)
        val manualStarted = CountDownLatch(1)
        val manualPid = AtomicInteger()
        val edited = fixture.input.copy(header = "Manual edit", end = fixture.input.end.plusHours(1))
        try {
            val followUp = executor.submit<DowntimeOutcome> {
                TransactionTemplate(transactionManager).execute {
                    val outcome = apply(fixture, true, MessageKind.COMPLETION)
                    followUpApplied.countDown()
                    check(releaseFollowUp.await(10, TimeUnit.SECONDS))
                    outcome
                }!!
            }
            assertThat(followUpApplied.await(5, TimeUnit.SECONDS)).isTrue()
            val manual = executor.submit {
                TransactionTemplate(transactionManager).execute {
                    manualPid.set(jdbc.queryForObject("SELECT pg_backend_pid()", Int::class.java)!!)
                    manualStarted.countDown()
                    activities.update(fixture.activityId, edited)
                }
            }
            assertThat(manualStarted.await(5, TimeUnit.SECONDS)).isTrue()
            assertThat(awaitBlocked(manualPid.get())).isTrue()
            releaseFollowUp.countDown()

            assertThat(followUp.get(10, TimeUnit.SECONDS).status).isEqualTo(SlackDowntimeMessageStatus.ACTIVITY_UPDATED)
            manual.get(10, TimeUnit.SECONDS)
            val saved = activities.get(fixture.activityId)
            assertThat(saved.source).isEqualTo(ActivitySource.MANUAL)
            assertThat(saved.header).isEqualTo(edited.header)
            assertThat(saved.end.toLocalDateTime()).isEqualTo(edited.end)
        } finally {
            releaseFollowUp.countDown()
            executor.shutdown()
            check(executor.awaitTermination(15, TimeUnit.SECONDS))
            cleanUp(fixture)
        }
    }
}
