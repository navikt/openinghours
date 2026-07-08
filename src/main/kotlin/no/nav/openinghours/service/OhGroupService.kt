package no.nav.openinghours.service

import no.nav.openinghours.model.db.OhGroup
import no.nav.openinghours.model.db.OhGroupRepository
import no.nav.openinghours.model.db.RuleRepository
import no.nav.openinghours.model.db.Service as ServiceEntity
import no.nav.openinghours.model.db.ServiceOhGroupRepository
import no.nav.openinghours.model.db.ServiceRepository
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

data class GroupAssociations(val services: List<ServiceEntity>, val groups: List<OhGroup>)

@Service
class OhGroupService(
    private val repo: OhGroupRepository,
    private val serviceRepo: ServiceOhGroupRepository,
    private val serviceRepository: ServiceRepository,
    private val ruleRepository: RuleRepository
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

    @Transactional(readOnly = true)
    fun getAssociationsByGroupId(groupId: UUID): GroupAssociations {
        if (!repo.existsById(groupId)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Group not found: $groupId")
        }
        val serviceIds = serviceRepo.findServiceIdsByGroupId(groupId)
        val services = if (serviceIds.isEmpty()) emptyList() else serviceRepository.findAllById(serviceIds)
        val groups = repo.findAllReferencing(groupId.toString())
        return GroupAssociations(services = services, groups = groups)
    }

    @Transactional(readOnly = true)
    fun getOhGroupForService(serviceId: UUID): OhGroup {
        val groupId = serviceRepo.findGroupIdByServiceId(serviceId)
            ?: throw ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "No opening-hours group linked to service id $serviceId"
            )
        return repo.findById(groupId).orElseThrow {
            ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Group not found: $groupId"
            )
        }
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
    fun removeGroupFromGroup(parentGroupId: UUID, childGroupId: UUID): OhGroup {
        val parent = repo.findById(parentGroupId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Group not found: $parentGroupId")
        }
        if (!repo.existsById(childGroupId)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Group not found: $childGroupId")
        }
        val childIdStr = childGroupId.toString()
        if (parent.ruleGroupIds == null || childIdStr !in parent.ruleGroupIds!!) {
            throw ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Group $childGroupId is not a member of group $parentGroupId"
            )
        }
        if (ruleRepository.existsById(childGroupId)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Group not found: $childGroupId")
        }
        parent.ruleGroupIds = parent.ruleGroupIds!!
            .filter { it != childIdStr }
            .toTypedArray()
            .ifEmpty { null }
        return try {
            repo.saveAndFlush(parent)
                .also { log.info("Removed child group {} from oh_group id={}", childGroupId, parentGroupId) }
        } catch (e: Exception) {
            log.error(
                "Remove group from group failed parentGroupId={} childGroupId={} msg={}",
                parentGroupId, childGroupId, e.message, e
            )
            throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Remove group from group: ${e.message}", e)
        }
    }

    @Transactional
    fun removeRuleFromGroup(groupId: UUID, ruleId: UUID): OhGroup {
        val group = repo.findById(groupId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Group not found: $groupId")
        }
        if (!ruleRepository.existsById(ruleId)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Rule not found: $ruleId")
        }
        val ruleIdStr = ruleId.toString()
        if (group.ruleGroupIds == null || ruleIdStr !in group.ruleGroupIds!!) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Rule $ruleId is not a member of group $groupId")
        }
        group.ruleGroupIds = group.ruleGroupIds!!
            .filter { it != ruleIdStr }
            .toTypedArray()
            .ifEmpty { null }
        return try {
            repo.saveAndFlush(group)
                .also { log.info("Removed rule {} from oh_group id={}", ruleId, groupId) }
        } catch (e: Exception) {
            log.error("Remove rule from group failed groupId={} ruleId={} msg={}", groupId, ruleId, e.message, e)
            throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Remove rule from group: ${e.message}", e)
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

            val unlinked = serviceRepo.deleteAllLinksByGroup(id)
            if (unlinked > 0) log.info("Removed {} service_oh_group links for group id={}", unlinked, id)

            repo.deleteById(id)

            log.info("Deleted oh_group id={}", id)
            true
        } catch (e: Exception) {
            log.error("Delete oh_group failed id={} msg={}", id, e.message, e)
            throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Delete group: ${e.message}", e)
        }
    }

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
