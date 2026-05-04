package no.nav.openinghours.service

import no.nav.openinghours.model.db.OhGroup
import no.nav.openinghours.repository.OhGroupRepository
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class OhGroupService(private val repository: OhGroupRepository) {

    fun upsertGroup(group: OhGroup) {
        group.updatedAt = group.updatedAt ?: Instant.now()
        if (group.ruleGroupIds.isNullOrEmpty()) {
            group.ruleGroupIds = null
        }
        repository.upsertGroup(
            id = group.id,
            name = group.name,
            createdAt = group.createdAt,
            updatedAt = group.updatedAt
        )
    }
}