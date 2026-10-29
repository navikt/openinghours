package no.nav.openinghours.controllers

import io.swagger.v3.oas.annotations.Operation
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Size
import no.nav.openinghours.service.ActivityInput
import no.nav.openinghours.service.ActivityService
import no.nav.openinghours.service.ActivityView
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

@RestController
@RequestMapping("/api/openinghours/activity")
@Validated
class ActivityController(
    private val service: ActivityService
) {
    @Operation(summary = "Get scheduled activities for a service today")
    @GetMapping("/service/{serviceId}/today")
    fun getTodayForService(@PathVariable serviceId: UUID): List<ActivityView> =
        service.getTodayForService(serviceId)

    @Operation(summary = "Get scheduled activities for a service on a date")
    @GetMapping("/service/{serviceId}")
    fun getForServiceOnDate(
        @PathVariable serviceId: UUID,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) date: LocalDate,
    ): List<ActivityView> = service.getForService(serviceId, date, date)

    @Operation(summary = "Get scheduled activities for a service over a date range (inclusive)")
    @GetMapping("/service/{serviceId}/range")
    fun getForServiceInRange(
        @PathVariable serviceId: UUID,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate,
    ): List<ActivityView> = service.getForService(serviceId, from, to)

    @Operation(summary = "Get scheduled activities for all services on a date")
    @GetMapping("/date")
    fun getForDate(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) date: LocalDate,
    ): List<ActivityView> = service.getForAllServices(date, date)

    @Operation(summary = "Get scheduled activities for all services over a date range (inclusive)")
    @GetMapping("/range")
    fun getForRange(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate,
    ): List<ActivityView> = service.getForAllServices(from, to)

    @Operation(summary = "Get an activity by id")
    @GetMapping("/{id}")
    fun get(@PathVariable id: UUID): ActivityView = service.get(id)

    @Operation(summary = "Create an activity covering one or more services. start/end are local date-times in the configured timezone (Europe/Oslo).")
    @PostMapping
    fun create(@Valid @RequestBody request: ActivityRequest): ActivityView = service.create(request.toInput())

    @Operation(summary = "Create several activities at once (all or nothing)")
    @PostMapping("/batch")
    fun createBatch(@RequestBody requests: List<ActivityRequest>): List<ActivityView> =
        service.createAll(requests.map { it.toInput() })

    @Operation(summary = "Replace an activity")
    @PutMapping("/{id}")
    fun update(@PathVariable id: UUID, @Valid @RequestBody request: ActivityRequest): ActivityView =
        service.update(id, request.toInput())

    @Operation(summary = "Delete an activity")
    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: UUID): Boolean = service.delete(id)
}

data class ActivityRequest(
    @field:NotEmpty val serviceIds: Set<UUID>,
    @field:NotBlank @field:Size(max = ActivityService.HEADER_MAX) val header: String,
    @field:NotBlank val message: String,
    val start: LocalDateTime,
    val end: LocalDateTime,
    val onlyShowForNavEmployees: Boolean = false
) {
    fun toInput() = ActivityInput(serviceIds, header, message, start, end, onlyShowForNavEmployees)
}
