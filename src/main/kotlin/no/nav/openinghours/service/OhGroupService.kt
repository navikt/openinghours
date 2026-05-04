package no.nav.openinghours.service

import no.nav.openinghours.model.db.OhGroup
import no.nav.openinghours.repository.OhGroupRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class OhGroupService(private val repository: OhGroupRepository) {

    @Transactional
    fun upsertGroup(group: OhGroup): OhGroup =
        repository.findById(group.id)
            .map { existing ->
                existing.name = group.name
                existing.ruleGroupIds = group.ruleGroupIds
                existing.updatedAt = Instant.now()
                repository.save(existing)
            }
            .orElseGet {
                repository.save(OhGroup(
                    id = group.id,
                    name = group.name,
                    ruleGroupIds = group.ruleGroupIds
                ))
            }
}