package no.nav.openinghours.service

import no.nav.openinghours.model.db.Group
import no.nav.openinghours.model.db.GroupRepository
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.util.*
import java.util.stream.Collectors

@Service
class GroupService (private val grouprepo: GroupRepository

) {
    private val log = LoggerFactory.getLogger(GroupService::class.java)

    fun upsert(
        name: String,
        ruleGroupIds: Array<UUID>?
    ): Group {
        return try {
            if (name.isBlank()) {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Name must not be blank")
            }

            // Check for duplicate name
            if (grouprepo.findByName(name) != null) {
                throw ResponseStatusException(HttpStatus.CONFLICT, "A group with the name '$name' already exists")
            }

            // Validate ruleGroupIds
            if (ruleGroupIds != null) {
                if (ruleGroupIds.any { it.toString().isBlank() }) {
                    throw ResponseStatusException(HttpStatus.BAD_REQUEST, "ruleGroupIds contains invalid UUIDs")
                }
            }

            val entity = Group.create(
                UUID.randomUUID(),
                name,
                ruleGroupIds?.toList() ?: emptyList()
            )

            // Check for circular dependency
            if (hasCircularDependency(entity, grouprepo)) {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Circular dependency detected in ruleGroupIds")
            }

            grouprepo.save(entity).also {
                log.info("Successfully upserted group with name={}", name)
            }

        } catch (e: Exception) {
            log.error("Upsert group failed name={} msg={}", name, e.message, e)
            throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Upsert group: ${e.message}", e)
        }
    }

    fun hasCircularDependency(group: Group, groupRepo: GroupRepository): Boolean {
        val visitedGroups = mutableSetOf<UUID>()

        fun checkCircularDependency(currentGroup: Group): Boolean {
            if (visitedGroups.contains(currentGroup.id)) {
                return true // Circular dependency detected
            }

            visitedGroups.add(currentGroup.id)

            val subGroups = currentGroup.ruleGroupIds.mapNotNull { groupRepo.findById(it).orElse(null) }
            for (subGroup in subGroups) {
                if (checkCircularDependency(subGroup)) {
                    return true
                }
            }

            visitedGroups.remove(currentGroup.id)
            return false
        }

        return checkCircularDependency(group)
    }



}