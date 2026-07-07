package no.nav.openinghours.controllers

import io.swagger.v3.oas.annotations.Operation
import no.nav.openinghours.model.db.OhGroup
import no.nav.openinghours.service.GroupAssociations
import no.nav.openinghours.service.OhGroupService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

@RestController
@RequestMapping("/api/openinghours/group")
class OhGroupController(
    private val service: OhGroupService
) {
    @Operation(summary = "Create a new opening hours group")
    @PostMapping
    fun save(
        @RequestParam name: String,
        @RequestBody(required = false) ruleGroupIds: List<UUID>?
    ): OhGroup = service.save(name, ruleGroupIds ?: emptyList())

    @Operation(summary = "Get an opening hours group by id")
    @GetMapping("/{id}")
    fun get(@PathVariable id: UUID): OhGroup = service.get(id)

    @Operation(summary = "Get all opening hours groups")
    @GetMapping
    fun getAll(): List<OhGroup> = service.getAll()

    @Operation(summary = "Update an opening hours group by id")
    @PutMapping("/{id}")
    fun update(
        @PathVariable id: UUID,
        @RequestBody request: OhGroupRequest
    ): OhGroup = service.update(
        id,
        request.name,
        request.ruleGroupIds
    )

    @Operation(summary = "Delete an opening hours group by id. Returns 409 with a warning if the group is linked to services or referenced by other groups; pass ?confirm=true to proceed with deletion.")
    @DeleteMapping("/{id}")
    fun delete(
        @PathVariable id: UUID,
        @RequestParam(required = false, defaultValue = "false") confirm: Boolean
    ): Boolean {
        if (!confirm) {
            val associations = service.getAssociationsByGroupId(id)
            val serviceCount = associations.services.size
            val groupCount = associations.groups.size
            if (serviceCount > 0 || groupCount > 0) {
                val parts = buildList {
                    if (serviceCount > 0) {
                        val names = associations.services.joinToString(", ") { it.name }
                        add("$serviceCount service(s): $names")
                    }
                    if (groupCount > 0) {
                        val names = associations.groups.joinToString(", ") { it.name }
                        add("$groupCount group(s): $names")
                    }
                }
                throw ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Group is in use by ${parts.joinToString(" and ")}. Pass ?confirm=true to delete anyway."
                )
            }
        }
        return service.delete(id)
    }

    @Operation(summary = "Remove a child group from an opening hours group")
    @DeleteMapping("/{parentGroupId}/groups/{childGroupId}")
    fun removeGroup(@PathVariable parentGroupId: UUID, @PathVariable childGroupId: UUID): OhGroup =
        service.removeGroupFromGroup(parentGroupId, childGroupId)

    @Operation(summary = "Remove a rule from an opening hours group")
    @DeleteMapping("/{groupId}/rules/{ruleId}")
    fun removeRule(@PathVariable groupId: UUID, @PathVariable ruleId: UUID): OhGroup =
        service.removeRuleFromGroup(groupId, ruleId)

    @Operation(summary = "Get the opening hours group assigned to a service")
    @GetMapping("/service/{serviceId}")
    fun getForService(@PathVariable serviceId: UUID): OhGroup = service.getOhGroupForService(serviceId)

    @Operation(summary = "Get all services and groups associated with a group")
    @GetMapping("/{id}/associations")
    fun getAssociations(@PathVariable id: UUID): GroupAssociations = service.getAssociationsByGroupId(id)
}

data class OhGroupRequest(
    val name: String?,
    val ruleGroupIds: List<UUID>?
)
