package no.nav.openinghours.controllers

import io.swagger.v3.oas.annotations.Operation
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import no.nav.openinghours.model.db.Service
import no.nav.openinghours.model.db.ServiceType
import no.nav.openinghours.service.ServiceService
import no.nav.openinghours.statusplattform.StatusplattformApiException
import no.nav.openinghours.statusplattform.StatusplattformSyncService
import no.nav.openinghours.statusplattform.SyncResult
import org.springframework.http.HttpStatus
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

@RestController
@RequestMapping("/api/openinghours/service")
@Validated
class ServiceController(
    private val service: ServiceService,
    private val statusplattformSync: StatusplattformSyncService
) {
    @Operation(summary = "Add new services from Statusplattform (existing services are left unchanged)")
    @PostMapping("/sync")
    fun syncFromStatusplattform(): SyncResult =
        try {
            statusplattformSync.sync()
        } catch (e: StatusplattformApiException) {
            throw ResponseStatusException(
                HttpStatus.BAD_GATEWAY,
                e.message,
                e
            )
        } catch (e: ResourceAccessException) {
            throw ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Statusplattform is unreachable: ${e.rootCause?.message ?: e.message}",
                e
            )
        }

    @Operation(summary = "Get a service by id")
    @GetMapping("/{id}")
    fun getService(@PathVariable id: UUID): Service = service.get(id)

    @Operation(summary = "Get all services")
    @GetMapping
    fun getServices(@RequestParam(required = false) type: ServiceType?): List<Service> =
        if (type == null) service.getAll() else service.getAllByType(type)

    @Operation(summary = "Create a new service")
    @PostMapping
    fun newService(@Valid @RequestBody request: ServiceRequest): Service =
        service.save(
            name = request.name,
            type = request.type,
            team = request.team,
            monitorlink = request.monitorlink,
            logglink = request.logglink,
            description = request.description,
            ohGroupId = request.ohGroupId
        )

    @Operation(summary = "Update a service")
    @PutMapping("/{id}")
    fun updateService(@PathVariable id: UUID, @Valid @RequestBody request: ServiceRequest): Service =
        service.update(
            id = id,
            name = request.name,
            type = request.type,
            team = request.team,
            monitorlink = request.monitorlink,
            logglink = request.logglink,
            description = request.description,
            ohGroupId = request.ohGroupId
        )

    @Operation(summary = "Delete a service")
    @DeleteMapping("/{id}")
    fun deleteService(@PathVariable id: UUID): Boolean = service.delete(id)

    @Operation(summary = "Set opening-hours group for a service")
    @PutMapping("/{id}/oh-group/{groupId}")
    fun setOhGroup(@PathVariable id: UUID, @PathVariable groupId: UUID) =
        service.setOhGroup(id, groupId)

    @Operation(summary = "Remove opening-hours group from a service")
    @DeleteMapping("/{id}/oh-group")
    fun removeOhGroup(@PathVariable id: UUID) = service.removeOhGroup(id)

    @Operation(summary = "List opening-hours groups linked to a service")
    @GetMapping("/{id}/oh-groups")
    fun getOhGroups(@PathVariable id: UUID): List<UUID> = service.getOhGroupIdsForService(id)
}

data class ServiceRequest(
    @field:NotBlank val name: String,
    val type: ServiceType,
    @field:NotBlank val team: String,
    val monitorlink: String? = null,
    val logglink: String? = null,
    val description: String? = null,
    val ohGroupId: UUID? = null
)