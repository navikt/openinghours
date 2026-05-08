package no.nav.openinghours.controllers

import io.swagger.v3.oas.annotations.Operation
import jakarta.validation.constraints.NotBlank
import no.nav.openinghours.model.db.OhGroup
import no.nav.openinghours.service.OhGroupService
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*
import java.util.UUID

data class OhGroupRequest(val name: String?, val ruleGroupIds: List<UUID>?)

@RestController
@RequestMapping("/api/openinghours/group")
@Validated
class OhGroupController(
    private val service: OhGroupService
) {
    @Operation(summary = "Create a new opening hours group")
    @PostMapping
    fun save(
        @RequestParam @NotBlank name: String,
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
    ): OhGroup = service.update(id, request.name, request.ruleGroupIds)

    @Operation(summary = "Delete an opening hours group by id")
    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: UUID): Boolean = service.delete(id)

}