package no.nav.openinghours.controllers

import io.swagger.v3.oas.annotations.Operation
import no.nav.openinghours.model.db.OhGroup
import no.nav.openinghours.model.db.Rule
import no.nav.openinghours.service.RuleService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import java.util.*

@RestController
@RequestMapping("/api/openinghours/rule")
class RuleController(
    private val service: RuleService
) {
    @Operation(summary = "List rules anchored to the given year, e.g. year=2024. Recurring rules (wildcard year) are never included. Only years at least three years in the past can be selected.")
    @GetMapping("/outdated")
    fun getOutdated(
        @RequestParam(required = false) year: Int?
    ): List<Rule> = service.findByYear(requireYear(year))

    @Operation(summary = "Delete all rules from the given year, e.g. year=2024. Returns 409 listing the affected rules unless ?confirm=true is passed. Only years at least three years in the past can be selected.")
    @DeleteMapping("/outdated")
    fun deleteOutdated(
        @RequestParam(required = false) year: Int?,
        @RequestParam(required = false, defaultValue = "false") confirm: Boolean
    ): List<Rule> {
        val selectedYear = requireYear(year)
        if (!confirm) {
            val candidates = service.findByYear(selectedYear)
            if (candidates.isNotEmpty()) {
                val names = candidates.joinToString(", ") { it.name }
                throw ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "${candidates.size} rule(s) from $selectedYear would be deleted: $names. Pass ?confirm=true to proceed."
                )
            }
            return emptyList()
        }
        return service.deleteByYear(selectedYear)
    }

    /**
     * The year is deliberately optional at the framework level so that a missing value reaches us
     * as `null`. Declaring it required would let Spring reject the request first, with a generic
     * message that never mentions which parameter is expected or why.
     */
    private fun requireYear(year: Int?): Int =
        year ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "You must specify the year")

    @Operation(summary = "Get opening hours rule id")
    @GetMapping("/{id}")
    fun get(@PathVariable id: UUID): Rule = service.get(id)

    @Operation(summary = "Upsert opening hours rule with name, rule, header, text, onlyShowForNavEmployees, and unstableOpeningHours")
    @PutMapping
    fun upsert(
        @RequestParam name: String,
        @RequestParam rule: String,
        @RequestParam(required = false) header: String?,
        @RequestParam(required = false) text: String?,
        @RequestParam(required = false, defaultValue = "false") onlyShowForNavEmployees: Boolean?,
        @RequestParam(required = false, defaultValue = "false") unstableOpeningHours: Boolean?
    ): Rule = service.upsert(
        name,
        rule,
        header,
        text,
        onlyShowForNavEmployees ?: false,
        unstableOpeningHours ?: false
    )

    @Operation(summary = "Delete opening hours rule by id. Returns 409 with a warning if the rule is used by one or more groups; pass ?confirm=true to proceed with deletion.")
    @DeleteMapping("/{id}")
    fun delete(
        @PathVariable id: UUID,
        @RequestParam(required = false, defaultValue = "false") confirm: Boolean
    ): Boolean {
        if (!confirm) {
            val groups = try {
                service.getGroupsByRuleId(id)
            } catch (e: ResponseStatusException) {
                if (e.statusCode == HttpStatus.NOT_FOUND) emptyList() else throw e
            }
            if (groups.isNotEmpty()) {
                val names = groups.joinToString(", ") { it.name }
                throw ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Rule is used by ${groups.size} group(s): $names. Pass ?confirm=true to delete anyway."
                )
            }
        }
        return service.delete(id)
    }

    @Operation(summary = "Get all opening hours rules")
    @GetMapping
    fun getAll(): List<Rule> = service.getAll()

    @Operation(summary = "Get all groups associated with a rule")
    @GetMapping("/{id}/groups")
    fun getGroupsByRuleId(@PathVariable id: UUID): List<OhGroup> = service.getGroupsByRuleId(id)

    @Operation(summary = "Update opening hours rule by id with name, rule, header, text, onlyShowForNavEmployees, and unstableOpeningHours")
    @PatchMapping("/{id}")
    fun update(
        @PathVariable id: UUID,
        @RequestParam(required = false) name: String?,
        @RequestParam(required = false) rule: String?,
        @RequestParam(required = false) header: String?,
        @RequestParam(required = false) text: String?,
        @RequestParam(required = false) onlyShowForNavEmployees: Boolean?,
        @RequestParam(required = false) unstableOpeningHours: Boolean?
    ): Rule = service.update(id, name, rule, header, text, onlyShowForNavEmployees, unstableOpeningHours)


}
