package no.nav.openinghours.model.db

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface OhGroupRepository : JpaRepository<OhGroup, UUID> {
    fun findByName(name: String): OhGroup?
}