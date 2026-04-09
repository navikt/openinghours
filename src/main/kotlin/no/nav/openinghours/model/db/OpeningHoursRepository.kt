package no.nav.openinghours.model.db

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface OpeningHoursRepository : JpaRepository<OpeningHours, String> {
    fun findById(id: UUID): OpeningHours?

    fun findByName(name: String): OpeningHours?
}
