package no.nav.openinghours.service

import no.nav.openinghours.model.db.Activity
import no.nav.openinghours.model.db.ActivityRepository
import no.nav.openinghours.model.db.ServiceRepository
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.UUID

/** Input for creating or replacing an activity. [start]/[end] are local times in the configured timezone. */
data class ActivityInput(
    val serviceIds: Set<UUID>,
    val header: String,
    val message: String,
    val start: LocalDateTime,
    val end: LocalDateTime,
    val onlyShowForNavEmployees: Boolean = false
)

data class ActivityView(
    val id: UUID,
    val serviceIds: List<UUID>,
    val header: String,
    val message: String,
    val start: OffsetDateTime,
    val end: OffsetDateTime,
    val onlyShowForNavEmployees: Boolean,
    /** True when the current time is within [start, end). */
    val ongoing: Boolean
)

@Service
class ActivityService(
    private val repo: ActivityRepository,
    private val serviceRepo: ServiceRepository,
    private val clock: Clock
) {
    private val log = LoggerFactory.getLogger(ActivityService::class.java)

    companion object {
        const val HEADER_MAX = 200
    }

    @Transactional(readOnly = true)
    fun get(id: UUID): ActivityView = toView(findActivity(id))

    @Transactional
    fun create(input: ActivityInput): ActivityView = createAll(listOf(input)).single()

    /** Creates all activities in one transaction; nothing is saved if any input is invalid. */
    @Transactional
    fun createAll(inputs: List<ActivityInput>): List<ActivityView> {
        if (inputs.isEmpty()) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one activity is required")
        inputs.forEachIndexed { index, input -> validate(input, if (inputs.size > 1) "activities[$index]: " else "") }
        val saved = repo.saveAllAndFlush(inputs.map { input ->
            Activity.create(
                serviceIds = input.serviceIds,
                header = input.header.trim(),
                message = input.message.trim(),
                startAt = toInstant(input.start),
                endAt = toInstant(input.end),
                onlyShowForNavEmployees = input.onlyShowForNavEmployees
            )
        })
        log.info("Created {} activit(y/ies)", saved.size)
        return saved.map(::toView)
    }

    @Transactional
    fun update(id: UUID, input: ActivityInput): ActivityView {
        val entity = findActivity(id)
        validate(input)
        entity.apply {
            serviceIds.clear()
            serviceIds.addAll(input.serviceIds)
            header = input.header.trim()
            message = input.message.trim()
            startAt = toInstant(input.start)
            endAt = toInstant(input.end)
            onlyShowForNavEmployees = input.onlyShowForNavEmployees
        }
        return toView(repo.saveAndFlush(entity)).also { log.info("Updated activity id={}", id) }
    }

    @Transactional
    fun delete(id: UUID): Boolean {
        if (!repo.existsById(id)) return false
        repo.deleteById(id)
        log.info("Deleted activity id={}", id)
        return true
    }

    @Transactional(readOnly = true)
    fun getTodayForService(serviceId: UUID): List<ActivityView> {
        val today = LocalDate.now(clock)
        return getForService(serviceId, today, today)
    }

    /** Activities covering [serviceId] that overlap any part of the days [from]..[to] (inclusive). */
    @Transactional(readOnly = true)
    fun getForService(serviceId: UUID, from: LocalDate, to: LocalDate): List<ActivityView> {
        requireValidRange(from, to)
        if (!serviceRepo.existsById(serviceId)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Service not found: $serviceId")
        }
        return repo.findOverlappingForService(serviceId, startOfDay(from), startOfDay(to.plusDays(1))).map(::toView)
    }

    /** Activities for all services overlapping any part of the days [from]..[to] (inclusive). */
    @Transactional(readOnly = true)
    fun getForAllServices(from: LocalDate, to: LocalDate): List<ActivityView> {
        requireValidRange(from, to)
        return repo.findOverlapping(startOfDay(from), startOfDay(to.plusDays(1))).map(::toView)
    }

    private fun findActivity(id: UUID): Activity = repo.findById(id).orElseThrow {
        ResponseStatusException(HttpStatus.NOT_FOUND, "Activity not found: $id")
    }

    private fun validate(input: ActivityInput, prefix: String = "") {
        fun bad(msg: String): Nothing = throw ResponseStatusException(HttpStatus.BAD_REQUEST, prefix + msg)
        if (input.serviceIds.isEmpty()) bad("At least one service is required")
        if (input.header.isBlank()) bad("Header must not be blank")
        if (input.header.trim().length > HEADER_MAX) bad("Header must be at most $HEADER_MAX characters")
        if (input.message.isBlank()) bad("Message must not be blank")
        if (!toInstant(input.end).isAfter(toInstant(input.start))) bad("End must be after start")
        val existing = serviceRepo.findAllById(input.serviceIds).map { it.id }.toSet()
        val missing = input.serviceIds - existing
        if (missing.isNotEmpty()) bad("Service(s) not found: ${missing.joinToString(", ")}")
    }

    private fun requireValidRange(from: LocalDate, to: LocalDate) {
        if (from.isAfter(to)) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "'from' date must not be after 'to' date")
    }

    private fun toInstant(local: LocalDateTime): Instant = local.atZone(clock.zone).toInstant()

    private fun startOfDay(date: LocalDate): Instant = date.atStartOfDay(clock.zone).toInstant()

    private fun toView(a: Activity): ActivityView {
        val now = clock.instant()
        return ActivityView(
            id = a.id,
            serviceIds = a.serviceIds.sorted(),
            header = a.header,
            message = a.message,
            start = a.startAt.atZone(clock.zone).toOffsetDateTime(),
            end = a.endAt.atZone(clock.zone).toOffsetDateTime(),
            onlyShowForNavEmployees = a.onlyShowForNavEmployees,
            ongoing = !now.isBefore(a.startAt) && now.isBefore(a.endAt)
        )
    }
}
