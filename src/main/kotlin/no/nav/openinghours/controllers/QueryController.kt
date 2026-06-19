package no.nav.openinghours.controllers

import io.swagger.v3.oas.annotations.Operation
import no.nav.openinghours.evaluator.NorwegianPublicHolidays
import no.nav.openinghours.evaluator.OpeningHoursEvaluator
import no.nav.openinghours.service.DisplayDataResult
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
    private val norwegianPublicHolidays: NorwegianPublicHolidays,
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
        val serviceName = serviceService.get(serviceId).name
        return buildResponse(groupId, serviceId, date, serviceName = serviceName)
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
        val serviceName = serviceService.get(serviceId).name
        // Capture the clock once for the entire range so every entry is evaluated
        // against the same instant — prevents isOpen semantics from changing mid-iteration
        // if processing straddles midnight.
        val now = LocalDateTime.now(clock)
        return from.datesUntil(to.plusDays(1)).map { date ->
            val result = lookupService.getDisplayDataOrDefault(groupId, date)
            buildResponse(groupId, serviceId, date, now, result, serviceName = serviceName)
        }.toList()
    }

    @Operation(summary = "Get opening hours for a group on a date")
    @GetMapping("/group/{groupId}")
    fun queryByGroup(
        @PathVariable groupId: UUID,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) date: LocalDate,
    ): QueryResponse = buildResponse(groupId, null, date)

    private fun buildResponse(
        groupId: UUID,
        serviceId: UUID?,
        date: LocalDate,
        now: LocalDateTime = LocalDateTime.now(clock),
        displayDataResult: DisplayDataResult? = null,
        serviceName: String? = null,
    ): QueryResponse {
        val displayData = displayDataResult?.data ?: lookupService.getDisplayData(groupId, date)
        val warningMessage = displayDataResult?.warningMessage
        val hours = displayData.openingHours ?: "00:00-23:59"
        val today = now.toLocalDate()
        val nowTime = now.toLocalTime()
        val isToday = date == today
        val parsed = OpeningHoursEvaluator.parseHoursRange(hours)
        val (openTime, closeTime) = if (parsed != null) {
            parsed.first.toString() to parsed.second.toString()
        } else {
            // Malformed hours: align fallback with isOpen semantics so the two fields
            // never contradict each other (computeIsOpenOnDate returns false for
            // malformed input on today, and true on non-today).
            if (isToday) "00:00" to "00:00" else "00:00" to "23:59"
        }
        val isOpen = OpeningHoursEvaluator.computeIsOpenOnDate(hours, date, today, nowTime)

        return QueryResponse(
            resourceId = serviceId ?: groupId,
            serviceName = serviceName,
            date = date,
            isOpen = isOpen,
            openingTime = openTime,
            closingTime = closeTime,
            displayHeader = displayData.displayHeader,
            displayText = displayData.displayText,
            onlyShowForNavEmployees = displayData.onlyShowForNavEmployees,
            // redDay is true when the matched rule marks the day as a red day OR when the
            // queried date is an official Norwegian public holiday (helligdag / rød dag).
            redDay = displayData.redDay || norwegianPublicHolidays.isPublicHoliday(date),
            matchedRule = if (warningMessage == null && displayData.ruleName != null && displayData.rule != null) MatchedRule(displayData.ruleName, displayData.rule) else null,
            warningMessage = warningMessage,
        )
    }
}

data class QueryResponse(
    val resourceId: UUID,
    @field:com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    val serviceName: String?,
    val date: LocalDate,
    val isOpen: Boolean,
    val openingTime: String,
    val closingTime: String,
    val displayHeader: String?,
    val displayText: String?,
    val onlyShowForNavEmployees: Boolean,
    val redDay: Boolean,
    @field:com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    val matchedRule: MatchedRule?,
    @field:com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    val warningMessage: String? = null,
)

data class MatchedRule(
    val name: String,
    val rule: String,
)