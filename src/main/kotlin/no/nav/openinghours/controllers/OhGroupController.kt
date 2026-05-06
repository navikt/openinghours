package no.nav.openinghours.controllers

import io.swagger.v3.oas.annotations.Operation
import jakarta.validation.constraints.NotBlank
import no.nav.openinghours.model.db.OhGroup
import no.nav.openinghours.service.OhGroupService
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*
import java.util.UUID

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


}