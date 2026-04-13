package no.nav.openinghours.model.db

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface OpeningHoursRepository : JpaRepository<OpeningHours, UUID> {
    
    fun findByName(name: String): OpeningHours?
}
