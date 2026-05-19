package no.nav.openinghours.model.db

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface ServiceOhGroupRepository : JpaRepository<ServiceOhGroup, ServiceOhGroupId> {

    @Query(
        value = """
            SELECT g.*
            FROM service_oh_group s2g
            LEFT JOIN oh_group g ON s2g.group_id = g.id
            WHERE s2g.service_id = :serviceId
        """,
        nativeQuery = true
    )
    fun findOhGroupByServiceId(@Param("serviceId") serviceId: UUID): OhGroup?

    @Modifying
    @Query("DELETE FROM ServiceOhGroup s WHERE s.id.groupId = :groupId")
    fun deleteAllLinksByGroup(@Param("groupId") groupId: UUID): Int
}