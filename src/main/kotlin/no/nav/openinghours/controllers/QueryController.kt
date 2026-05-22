package no.nav.openinghours.controllers

import io.swagger.v3.oas.annotations.Operation
import no.nav.openinghours.evaluator.OpeningHoursDisplayData
import no.nav.openinghours.service.OpeningHoursLookupService
import no.nav.openinghours.service.ServiceService
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDate
import java.util.UUID

@RestController
@RequestMapping("/api/openinghours/query")
class QueryController(
    private val lookupService: OpeningHoursLookupService,
    private val serviceService: ServiceService,
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
        val hours = displayData.openingHours ?: "00:00-00:00"
        val parts = hours.split("-")
        val openTime = parts[0]
        val closeTime = parts[1]
        val isOpen = hours != "00:00-00:00"

        return QueryResponse(
            resourceId = serviceId ?: groupId,
            date = date,
            isOpen = isOpen,
            openingTime = openTime,
            closingTime = closeTime,
            matchedRule = if (displayData.ruleName != null) MatchedRule(displayData.ruleName, displayData.rule!!) else null,
        )
    }
}

data class QueryResponse(
    val resourceId: UUID,
    val date: LocalDate,
    val isOpen: Boolean,
    val openingTime: String,
    val closingTime: String,
    val matchedRule: MatchedRule?,
)

data class MatchedRule(
    val name: String,
    val rule: String,
)