package no.nav.openinghours.model.db

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface ServiceOhGroupRepository : JpaRepository<ServiceOhGroup, ServiceOhGroupId> {

    @Query(
        value = "SELECT group_id FROM service_oh_group WHERE service_id = :serviceId",
        nativeQuery = true
    )
    fun findGroupIdsByServiceId(@Param("serviceId") serviceId: UUID): List<UUID>

    @Modifying
    @Query("DELETE FROM ServiceOhGroup s WHERE s.id.groupId = :groupId")
    fun deleteAllLinksByGroup(@Param("groupId") groupId: UUID): Int
}