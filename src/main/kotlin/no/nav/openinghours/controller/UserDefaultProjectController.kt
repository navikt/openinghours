package no.nav.openinghours.controller

import io.swagger.v3.oas.annotations.Operation
import no.nav.openinghours.model.db.UserDefaultProject
import no.nav.openinghours.service.UserDefaultProjectService
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/user/default-project")
class UserDefaultProjectController(
    private val service: UserDefaultProjectService
) {
    @Operation(summary = "Get user default project")
    @GetMapping("/{userId}")
    fun get(@PathVariable userId: String): UserDefaultProject? = service.get(userId)

    @Operation(summary = "Upsert user default project")
    @PutMapping("/{userId}")
    fun upsert(
        @PathVariable userId: String,
        @RequestParam projectKey: String,
        @RequestParam projectName: String
    ): UserDefaultProject = service.upsert(userId, projectKey, projectName)
}
