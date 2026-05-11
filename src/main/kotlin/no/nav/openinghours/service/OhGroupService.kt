package no.nav.openinghours.service

import no.nav.openinghours.model.db.OhGroup
import no.nav.openinghours.model.db.OhGroupRepository
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
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
            ResponseStatusException(HttpStatus.NOT_FOUND, "Group not found: $id")
        }

    fun getAll(): List<OhGroup> =
        try {
            repo.findAll()
        } catch (e: Exception) {
            log.error("Fetch all groups failed msg={}", e.message, e)
            throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to fetch groups", e)
        }

    @Transactional
    fun update(id: UUID, name: String?, ruleGroupIds: List<UUID>?): OhGroup {
        val group = repo.findById(id).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Group not found: $id")
        }
        if (!name.isNullOrBlank()) group.name = name
        if (ruleGroupIds != null) {
            if (graphHasCycle(ruleGroupIds, selfId = id)) {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Group contains circular dependency")
            }
            group.ruleGroupIds = ruleGroupIds.map { it.toString() }.toTypedArray().ifEmpty { null }
        }
        return try {
            repo.saveAndFlush(group).also { log.info("Updated oh_group id={}", id) }
        } catch (e: ResponseStatusException) {
            throw e
        } catch (e: DataIntegrityViolationException) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Group with name '${group.name}' already exists")
        } catch (e: Exception) {
            log.error("Update oh_group failed id={} msg={}", id, e.message, e)
            throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Update group: ${e.message}", e)
        }
    }

    @Transactional
    fun delete(id: UUID): Boolean {
        return try {
            if (!repo.existsById(id)) return false

            val idStr = id.toString()
            val affected = repo.findAllReferencing(idStr)

            affected.forEach { parent ->
                parent.ruleGroupIds = parent.ruleGroupIds
                    ?.filter { it != idStr }
                    ?.toTypedArray()
                    ?.ifEmpty { null }
            }

            repo.saveAll(affected)
            repo.deleteById(id)

            log.info("Deleted oh_group id={}", id)
            true
        } catch (e: Exception) {
            log.error("Delete oh_group failed id={} msg={}", id, e.message, e)
            throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Delete group: ${e.message}", e)
        }
    }

    // DFS over the existing group graph starting from rootIds.
    // selfId is pre-placed on the stack so any path leading back to the group being updated is detected as a cycle.
    // Returns true if any cycle is reachable (i.e. a node is visited twice on the same DFS path).
    private fun graphHasCycle(rootIds: List<UUID>, selfId: UUID? = null): Boolean {
        val visited = mutableSetOf<UUID>()
        val onStack = mutableSetOf<UUID>()
        if (selfId != null) onStack += selfId

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

