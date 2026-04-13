package no.nav.openinghours.controllers

import io.swagger.v3.oas.annotations.Operation
import no.nav.openinghours.model.db.OpeningHours
import no.nav.openinghours.service.OpeningHoursService
import org.springframework.web.bind.annotation.*
import java.util.*

@RestController
@RequestMapping("/api/openinghours/rule")
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

    @Operation(summary = "Delete opening hours rule by id")
    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: UUID): Boolean {
        return service.delete(id)
    }

    @Operation(summary = "Get all opening hours rules")
    @GetMapping
    fun getAll(): List<OpeningHours> = service.getAll()

    @Operation(summary = "Update opening hours rule by id")
    @PatchMapping("/{id}")
    fun update(
        @PathVariable id: UUID,
        @RequestParam name: String,
        @RequestParam rule: String
    ): OpeningHours = service.update(id, name, rule)


}
