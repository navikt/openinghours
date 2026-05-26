package no.nav.openinghours.controllers

import io.swagger.v3.oas.annotations.Operation
import no.nav.openinghours.model.db.Rule
import no.nav.openinghours.service.RuleService
import org.springframework.web.bind.annotation.*
import java.util.*

@RestController
@RequestMapping("/api/openinghours/rule")
class RuleController(
    private val service: RuleService
) {
    @Operation(summary = "Get opening hours rule id")
    @GetMapping("/{id}")
    fun get(@PathVariable id: UUID): Rule? = service.get(id)

    @Operation(summary = "Upsert opening hours rule with name, rule, header, and text")
    @PutMapping
    fun upsert(
        @RequestParam name: String,
        @RequestParam rule: String,
        @RequestParam(required = false) header: String?,
        @RequestParam(required = false) text: String?,
        @RequestParam(required = false, defaultValue = "false") onlyShowForNavEmployees: Boolean?
    ): Rule = service.upsert(name, rule, header, text, onlyShowForNavEmployees ?: false)

    @Operation(summary = "Delete opening hours rule by id")
    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: UUID): Boolean {
        return service.delete(id)
    }

    @Operation(summary = "Get all opening hours rules")
    @GetMapping
    fun getAll(): List<Rule> = service.getAll()

    @Operation(summary = "Update opening hours rule by id with name, rule, header, and text")
    @PatchMapping("/{id}")
    fun update(
        @PathVariable id: UUID,
        @RequestParam(required = false) name: String?,
        @RequestParam(required = false) rule: String?,
        @RequestParam(required = false) header: String?,
        @RequestParam(required = false) text: String?,
        @RequestParam(required = false) onlyShowForNavEmployees: Boolean?
    ): Rule = service.update(id, name, rule, header, text, onlyShowForNavEmployees)


}
