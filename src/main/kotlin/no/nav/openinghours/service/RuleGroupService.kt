package no.nav.openinghours.service

import no.nav.openinghours.model.db.RuleGroup
import no.nav.openinghours.model.db.RuleGroupRepository
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.util.*

@Service
class RuleGroupService (private val rulegrouprepo: RuleGroupRepository

) {
    private val log = LoggerFactory.getLogger(RuleGroupService::class.java)

    fun upsert(
        name: String,
        ruleGroupId: UUID
    ): RuleGroup {
        return try {
            if (name.isBlank()) {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Name must not be blank")
            }

            // Check for duplicate name
            if (rulegrouprepo.findByName(name) != null) {
                throw ResponseStatusException(HttpStatus.CONFLICT, "A group with the name '$name' already exists")
            }

            // Check for circular dependency
            val existingRuleGroup = rulegrouprepo.findById(ruleGroupId).orElse(null)
            if (existingRuleGroup != null && hasCircularDependency(existingRuleGroup, rulegrouprepo)) {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Circular dependency detected in ruleGroupId")
            }

            // Create the RuleGroup
            val entity = RuleGroup.create(
                UUID.randomUUID(),
                name,
                listOf(ruleGroupId)
            )

            rulegrouprepo.save(entity).also {
                log.info("Successfully upserted group with name={}", name)
            }

        } catch (e: ResponseStatusException) {
            log.error("Upsert group failed name={} msg={}", name, e.reason, e)
            throw e
        } catch (e: Exception) {
            log.error("Upsert group failed name={} msg={}", name, e.message, e)
            throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Upsert group: ${e.message}", e)
        }
    }



    fun hasCircularDependency(ruleGroup: RuleGroup, rulegrouprepo: RuleGroupRepository): Boolean {
        val visitedGroups = mutableSetOf<UUID>()

        fun checkCircularDependency(currentRuleGroup: RuleGroup): Boolean {
            if (visitedGroups.contains(currentRuleGroup.id)) {
                return true // Circular dependency detected
            }

            visitedGroups.add(currentRuleGroup.id)

            val subGroupIds = currentRuleGroup.ruleGroupIds ?: emptyList()
            val subGroups = rulegrouprepo.findAllById(subGroupIds).toList()
            for (subGroup in subGroups) {
                if (checkCircularDependency(subGroup)) {
                    return true
                }
            }

            visitedGroups.remove(currentRuleGroup.id)
            return false
        }

        return checkCircularDependency(ruleGroup)
    }



}