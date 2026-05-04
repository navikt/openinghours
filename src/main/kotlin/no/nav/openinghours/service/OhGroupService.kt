package no.nav.openinghours.service

import no.nav.openinghours.model.db.OhGroup
import no.nav.openinghours.repository.OhGroupRepository
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class OhGroupService(private val repository: OhGroupRepository) {

    fun upsertGroup(group: OhGroup): OhGroup {
        group.updatedAt = Instant.now()
        return repository.save(group)
    }
}