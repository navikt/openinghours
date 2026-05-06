package no.nav.openinghours.service

import no.nav.openinghours.model.db.OhGroup
import no.nav.openinghours.model.db.OhGroupRepository
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

@Service
class OhGroupService(
    private val repo: OhGroupRepository
) {
    private val log = LoggerFactory.getLogger(OhGroupService::class.java)

    fun save(name: String, ruleGroupIds: List<UUID>): OhGroup {
        if (name.isBlank()) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Name must not be blank")
        return try {
            val group = OhGroup.create(name = name, ruleGroupIds = ruleGroupIds)
            if (hasCircularDependency(group.id, ruleGroupIds)) {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Group contains circular dependency")
            }
            repo.save(group).also { log.info("Saved oh_group name={}", name) }
        } catch (e: ResponseStatusException) {
            throw e
        } catch (e: DataIntegrityViolationException) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Group with name '$name' already exists")
        } catch (e: Exception) {
            log.error("Save oh_group failed name={} msg={}", name, e.message, e)
            throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Save group: ${e.message}", e)
        }
    }

    private fun hasCircularDependency(groupId: UUID, candidateIds: List<UUID>): Boolean {
        if (candidateIds.contains(groupId)) return true
        return candidateIds.any { childId ->
            repo.findById(childId).map { child ->
                hasCircularDependency(groupId, child.ruleGroupUuids)
            }.orElse(false)
        }
    }
}
