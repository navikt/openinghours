package no.nav.openinghours.controllers

import io.swagger.v3.oas.annotations.Operation
import no.nav.openinghours.dailycache.OpeningHoursDailyCache
import no.nav.openinghours.evaluator.OpeningHoursDisplayData
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

@RestController
@RequestMapping("/api/openinghours/daily")
class DailyCacheController(
    private val cache: OpeningHoursDailyCache,
) {
    @Operation(summary = "Get today's cached opening hours for all services")
    @GetMapping
    fun getAll(): Map<UUID, OpeningHoursDisplayData> = cache.getAll()

    @Operation(summary = "Get today's cached opening hours for a service")
    @GetMapping("/{serviceId}")
    fun getForService(@PathVariable serviceId: UUID): OpeningHoursDisplayData =
        cache.getForService(serviceId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "No cached data for service $serviceId")
}