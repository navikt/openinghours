package no.nav.openinghours.controller

import io.swagger.v3.oas.annotations.Operation
import no.nav.openinghours.model.db.OpeningHours
import no.nav.openinghours.model.db.UserDefaultProject
import no.nav.openinghours.service.OpeningHoursService
import no.nav.openinghours.service.UserDefaultProjectService
import org.springframework.web.bind.annotation.*
import java.util.*

@RestController
@RequestMapping("/api/user/openinghours")
class OpeningHoursController(
    private val service: OpeningHoursService
) {
    @Operation(summary = "Get opening hours rule id")
    @GetMapping("/{id}")
    fun get(@PathVariable id: UUID): OpeningHours? = service.get(id)

    @Operation(summary = "Upsert opening hours rule with id, name and rule")
    @PutMapping("/{id}")
    fun upsert(
        @RequestParam name: String,
        @RequestParam rule: String
    ): OpeningHours = service.upsert(name, rule)
}
