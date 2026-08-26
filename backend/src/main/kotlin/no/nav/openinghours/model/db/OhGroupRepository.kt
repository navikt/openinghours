package no.nav.openinghours.model.db

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface OhGroupRepository : JpaRepository<OhGroup, UUID> {
    fun findByName(name: String): OhGroup?

    @Query(
        value = """
            SELECT *
            FROM oh_group
            WHERE :groupId = ANY(rule_group_ids)
        """,
        nativeQuery = true
    )
    fun findAllReferencing(@Param("groupId") groupId: String): List<OhGroup>
}