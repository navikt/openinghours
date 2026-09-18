package no.nav.openinghours.model.db

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface OhGroupRepository : JpaRepository<OhGroup, UUID> {
    fun findByName(name: String): OhGroup?

    /**
     * Locks the group row with a plain SQL `FOR UPDATE` so its empty/outdated state and any
     * concurrent service-link inserts can be safely revalidated before deletion. A native query is
     * used deliberately instead of `@Lock(PESSIMISTIC_WRITE)`: Hibernate's Postgres dialect maps
     * `PESSIMISTIC_WRITE` to the weaker `FOR NO KEY UPDATE`, which does not conflict with the
     * `FOR KEY SHARE` lock that inserting a service_oh_group row takes on this group via its
     * foreign key, so it would not actually block a concurrent link being added.
     */
    @Query(value = "SELECT * FROM oh_group WHERE id = :id FOR UPDATE", nativeQuery = true)
    fun findByIdForUpdate(@Param("id") id: UUID): OhGroup?

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