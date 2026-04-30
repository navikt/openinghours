package no.nav.openinghours.controller

import jakarta.validation.Valid
import no.nav.openinghours.model.db.OhGroup
import no.nav.openinghours.service.OhGroupService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/oh-group")
class OhGroupController(private val service: OhGroupService) {

    @PostMapping("/upsert")
    fun upsertGroup(@Valid @RequestBody group: OhGroup): ResponseEntity<String> {
        return try {
            service.upsertGroup(group)
            ResponseEntity("Group upserted successfully", HttpStatus.OK)
        } catch (e: Exception) {
            ResponseEntity("Failed to upsert group: ${e.message}", HttpStatus.INTERNAL_SERVER_ERROR)
        }
    }
}