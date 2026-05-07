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
        if (graphHasCycle(ruleGroupIds)) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Group contains circular dependency")
        }
        return try {
            val group = OhGroup.create(name = name, ruleGroupIds = ruleGroupIds)
            repo.saveAndFlush(group).also { log.info("Saved oh_group name={}", name) }
        } catch (e: ResponseStatusException) {
            throw e
        } catch (e: DataIntegrityViolationException) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Group with name '$name' already exists")
        } catch (e: Exception) {
            log.error("Save oh_group failed name={} msg={}", name, e.message, e)
            throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Save group: ${e.message}", e)
        }
    }

    fun get(id: UUID): OhGroup =
        repo.findById(id).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Group not found")
        }

    fun getAll(): List<OhGroup> =
        try {
            repo.findAll()
        } catch (e: Exception) {
            log.error("Fetch all groups failed msg={}", e.message, e)
            throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Fetch groups: ${e.message}", e)
        }




    // DFS over the existing group graph starting from ruleGroupIds.
    // Returns true if any cycle is reachable (i.e. a node is visited twice on the same DFS path).
    private fun graphHasCycle(rootIds: List<UUID>): Boolean {
        val visited = mutableSetOf<UUID>()
        val onStack = mutableSetOf<UUID>()

        fun dfs(id: UUID): Boolean {
            if (id in onStack) return true
            if (id in visited) return false
            visited += id
            onStack += id
            val children = repo.findById(id).map { it.ruleGroupUuids }.orElse(emptyList())
            if (children.any { dfs(it) }) return true
            onStack -= id
            return false
        }

        return rootIds.any { dfs(it) }
    }
}
