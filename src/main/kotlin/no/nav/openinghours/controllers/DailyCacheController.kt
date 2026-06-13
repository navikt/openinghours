package no.nav.openinghours.controllers

import io.swagger.v3.oas.annotations.Operation
import no.nav.openinghours.dailycache.OpeningHoursDailyCache
import no.nav.openinghours.dailycache.OpeningHoursDailyCacheScheduler
import no.nav.openinghours.evaluator.OpeningHoursDisplayData
import no.nav.openinghours.evaluator.OpeningHoursEvaluator
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.time.Clock
import java.time.LocalTime
import java.util.UUID

@RestController
@RequestMapping("/api/openinghours/daily")
class DailyCacheController(
    private val cache: OpeningHoursDailyCache,
    private val scheduler: OpeningHoursDailyCacheScheduler,
    private val clock: Clock,
) {
    @Operation(summary = "Get today's cached opening hours for all services")
    @GetMapping
    fun getAll(): Map<UUID, DailyCacheResponse> =
        cache.getAll().mapValues { (_, data) -> data.toResponse(clock) }

    @Operation(summary = "Get today's cached opening hours for a service")
    @GetMapping("/{serviceId}")
    fun getForService(@PathVariable serviceId: UUID): DailyCacheResponse =
        (cache.getForService(serviceId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "No cached data for service $serviceId"))
            .toResponse(clock)

    @Operation(summary = "Manually trigger a cache refresh")
    @PostMapping("/refresh")
    fun refresh() = scheduler.refresh()
}

data class DailyCacheResponse(
    val ruleName: String?,
    val rule: String?,
    val openingHours: String?,
    val displayHeader: String?,
    val displayText: String?,
    val onlyShowForNavEmployees: Boolean,
    val redDay: Boolean,
    val isOpen: Boolean,
)

private fun OpeningHoursDisplayData.toResponse(clock: Clock) = DailyCacheResponse(
    ruleName = ruleName,
    rule = rule,
    openingHours = openingHours,
    displayHeader = displayHeader,
    displayText = displayText,
    onlyShowForNavEmployees = onlyShowForNavEmployees,
    redDay = redDay,
    isOpen = OpeningHoursEvaluator.computeIsOpen(openingHours ?: "00:00-23:59", LocalTime.now(clock)),
)
