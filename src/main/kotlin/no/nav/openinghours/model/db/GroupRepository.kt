package no.nav.openinghours.model.db

import org.springframework.data.jpa.repository.JpaRepository
import java.util.*


interface GroupRepository : JpaRepository<Group, UUID> {
    fun findByName(name: String): Group?
}
