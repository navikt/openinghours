package no.nav.openinghours.controllers

import io.swagger.v3.oas.annotations.Operation
import no.nav.openinghours.model.db.Group
import no.nav.openinghours.model.db.OpeningHours
import no.nav.openinghours.service.GroupService
import no.nav.openinghours.service.OpeningHoursService
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/openinghours/group")
class GroupController(
    private val service: GroupService
) {

    @PostMapping("/OpeningHours/Group")
    @Operation(summary = "Upsert group name and rule group ids")
    @PutMapping
    fun upsert(
        @RequestParam name: String,
        @RequestParam ruleGroupIds: List<UUID>
    ): Group = service.upsert(name, ruleGroupIds.toTypedArray())



}