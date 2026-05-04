package no.nav.openinghours.controller

import jakarta.validation.Valid
import no.nav.openinghours.model.db.OhGroup
import no.nav.openinghours.service.OhGroupService
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/openinghours/oh-group")
class OhGroupController(private val service: OhGroupService) {

    @PostMapping("/upsert")
    fun upsertGroup(@Valid @RequestBody group: OhGroup): OhGroup = service.upsertGroup(group)
}