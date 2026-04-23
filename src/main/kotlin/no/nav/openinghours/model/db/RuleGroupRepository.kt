package no.nav.openinghours.model.db

import org.springframework.data.jpa.repository.JpaRepository
import java.util.*


interface RuleGroupRepository : JpaRepository<RuleGroup, UUID> {
    fun findByName(name: String): RuleGroup?
}
