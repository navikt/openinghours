package no.nav.openinghours.model.db

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface RuleRepository : JpaRepository<Rule, UUID> {
    
    fun findByName(name: String): Rule?
}
