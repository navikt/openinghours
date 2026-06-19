package no.nav.openinghours.controllers

import io.swagger.v3.oas.annotations.Operation
import no.nav.openinghours.dailycache.OpeningHoursDailyCache
import no.nav.openinghours.dailycache.OpeningHoursDailyCacheScheduler
import no.nav.openinghours.dailycache.ServiceCacheEntry
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
    fun getAll(): Map<UUID, DailyCacheResponse> {
        // Snapshot the current time once so every entry in the response is evaluated
        // at the same instant — avoids inconsistent isOpen values when a request
        // straddles an open/close boundary.
        val nowTime = LocalTime.now(clock)
        return cache.getAll().mapValues { (_, entry) -> entry.toResponse(nowTime) }
    }

    @Operation(summary = "Get today's cached opening hours for a service")
    @GetMapping("/{serviceId}")
    fun getForService(@PathVariable serviceId: UUID): DailyCacheResponse =
        (cache.getForService(serviceId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "No cached data for service $serviceId"))
            .toResponse(LocalTime.now(clock))

    @Operation(summary = "Manually trigger a cache refresh")
    @PostMapping("/refresh")
    fun refresh() = scheduler.refresh()
}

data class DailyCacheResponse(
    val serviceName: String,
    val ruleName: String?,
    val rule: String?,
    val openingHours: String?,
    val displayHeader: String?,
    val displayText: String?,
    val onlyShowForNavEmployees: Boolean,
    val redDay: Boolean,
    val isOpen: Boolean,
)

private fun ServiceCacheEntry.toResponse(nowTime: LocalTime) = DailyCacheResponse(
    serviceName = serviceName,
    ruleName = displayData.ruleName,
    rule = displayData.rule,
    openingHours = displayData.openingHours,
    displayHeader = displayData.displayHeader,
    displayText = displayData.displayText,
    onlyShowForNavEmployees = displayData.onlyShowForNavEmployees,
    redDay = displayData.redDay,
    isOpen = OpeningHoursEvaluator.computeIsOpen(displayData.openingHours ?: "00:00-23:59", nowTime),
)
