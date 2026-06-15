package no.nav.openinghours.controllers

import io.swagger.v3.oas.annotations.Operation
import no.nav.openinghours.evaluator.OpeningHoursEvaluator
import no.nav.openinghours.service.OpeningHoursLookupService
import no.nav.openinghours.service.ServiceService
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

@RestController
@RequestMapping("/api/openinghours/query")
class QueryController(
    private val lookupService: OpeningHoursLookupService,
    private val serviceService: ServiceService,
    private val clock: Clock,
) {

    @Operation(summary = "Get opening hours for a service on a date")
    @GetMapping("/service/{serviceId}")
    fun queryByService(
        @PathVariable serviceId: UUID,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) date: LocalDate,
    ): QueryResponse {
        val groupIds = serviceService.getOhGroupIdsForService(serviceId)
        if (groupIds.isEmpty()) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "No opening-hours group assigned to service $serviceId")
        }
        val groupId = groupIds.first()
        return buildResponse(groupId, serviceId, date)
    }

    @Operation(summary = "Get opening hours for a service over a date range")
    @GetMapping("/service/{serviceId}/range")
    fun queryByServiceRange(
        @PathVariable serviceId: UUID,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate,
    ): List<QueryResponse> {
        val groupIds = serviceService.getOhGroupIdsForService(serviceId)
        if (groupIds.isEmpty()) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "No opening-hours group assigned to service $serviceId")
        }
        val groupId = groupIds.first()
        if (from.isAfter(to)) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "'from' date must not be after 'to' date")
        }
        return from.datesUntil(to.plusDays(1)).map { buildResponse(groupId, serviceId, it) }.toList()
    }

    @Operation(summary = "Get opening hours for a group on a date")
    @GetMapping("/group/{groupId}")
    fun queryByGroup(
        @PathVariable groupId: UUID,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) date: LocalDate,
    ): QueryResponse = buildResponse(groupId, null, date)

    private fun buildResponse(groupId: UUID, serviceId: UUID?, date: LocalDate): QueryResponse {
        val displayData = lookupService.getDisplayData(groupId, date)
        val hours = displayData.openingHours ?: "00:00-23:59"
        // Snapshot the clock once so that the today-check for display-time fallback
        // and the isOpen computation are guaranteed to use the same instant, preventing
        // a midnight boundary from making them disagree.
        val now = LocalDateTime.now(clock)
        val today = now.toLocalDate()
        val nowTime = now.toLocalTime()
        val isToday = date == today
        val parts = hours.split("-")
        val (openTime, closeTime) =
            if (parts.size == 2) {
                val open = parts[0].trim().split(":").let { t ->
                    if (t.size != 2) null else runCatching { java.time.LocalTime.of(t[0].toInt(), t[1].toInt()) }.getOrNull()
                }
                val close = parts[1].trim().split(":").let { t ->
                    if (t.size != 2) null else runCatching { java.time.LocalTime.of(t[0].toInt(), t[1].toInt()) }.getOrNull()
                }
                if (open != null && close != null) {
                    open.toString() to close.toString()
                } else {
                    // Malformed time component: align fallback with isOpen semantics.
                    // computeIsOpenOnDate returns false for malformed input on today, and true on non-today,
                    // so mirror that here to keep openingTime/closingTime consistent with isOpen.
                    if (isToday) "00:00" to "00:00" else "00:00" to "23:59"
                }
            } else {
                // Malformed format (wrong number of parts): same date-aware fallback.
                if (isToday) "00:00" to "00:00" else "00:00" to "23:59"
            }
        val isOpen = OpeningHoursEvaluator.computeIsOpenOnDate(hours, date, today, nowTime)

        return QueryResponse(
            resourceId = serviceId ?: groupId,
            date = date,
            isOpen = isOpen,
            openingTime = openTime,
            closingTime = closeTime,
            displayHeader = displayData.displayHeader,
            displayText = displayData.displayText,
            onlyShowForNavEmployees = displayData.onlyShowForNavEmployees,
            redDay = displayData.redDay,
            matchedRule = if (displayData.ruleName != null && displayData.rule != null) MatchedRule(displayData.ruleName, displayData.rule) else null,
        )
    }
}

data class QueryResponse(
    val resourceId: UUID,
    val date: LocalDate,
    val isOpen: Boolean,
    val openingTime: String,
    val closingTime: String,
    val displayHeader: String?,
    val displayText: String?,
    val onlyShowForNavEmployees: Boolean,
    val redDay: Boolean,
    val matchedRule: MatchedRule?,
)

data class MatchedRule(
    val name: String,
    val rule: String,
)