package no.nav.openinghours.controllers

import io.swagger.v3.oas.annotations.Operation
import no.nav.openinghours.model.db.RuleGroup
import no.nav.openinghours.service.RuleGroupService
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/openinghours/group")
class RuleGroupController(
    private val service: RuleGroupService
) {

    @Operation(summary = "Upsert rule group name and rule group id")
    @PutMapping
    fun upsert(
        @RequestParam name: String,
        @RequestParam ruleGroupId: UUID
    ): RuleGroup = service.upsert(name, ruleGroupId)



}