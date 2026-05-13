package no.nav.openinghours.evaluator

import no.nav.openinghours.model.db.OhGroup
import no.nav.openinghours.model.db.OhGroupRepository
import no.nav.openinghours.model.db.Rule
import no.nav.openinghours.model.db.RuleRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

@Component
class OpeningHoursTreeResolver(
    private val ohGroupRepository: OhGroupRepository,
    private val ruleRepository: RuleRepository,
) {

    @Transactional(readOnly = true)
    fun resolve(groupId: UUID): ResolvedGroup {
        val root = ohGroupRepository.findById(groupId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Group not found: $groupId")
        }
        return resolveGroup(root, visited = mutableSetOf())
    }

    @Transactional(readOnly = true)
    fun resolve(group: OhGroup): ResolvedGroup = resolveGroup(group, visited = mutableSetOf())

    private fun resolveGroup(group: OhGroup, visited: MutableSet<UUID>): ResolvedGroup {
        if (!visited.add(group.id)) {
            throw ResponseStatusException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Circular group dependency detected at ${group.id}"
            )
        }

        val childIds = group.ruleGroupUuids
        val entries: List<ResolvedEntry> = if (childIds.isEmpty()) {
            emptyList()
        } else {
            val rulesById: Map<UUID, Rule> =
                ruleRepository.findAllById(childIds).associateBy { it.id }
            val missing = childIds.filterNot { rulesById.containsKey(it) }
            val groupsById: Map<UUID, OhGroup> =
                if (missing.isEmpty()) emptyMap()
                else ohGroupRepository.findAllById(missing).associateBy { it.id }

            childIds.mapNotNull { id ->
                rulesById[id]?.let { ResolvedRule(name = it.name, rule = it.rule) }
                    ?: groupsById[id]?.let { resolveGroup(it, visited) }
            }
        }

        visited.remove(group.id)
        return ResolvedGroup(name = group.name, entries = entries)
    }
}