package no.nav.openinghours.model.db

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface ServiceRepository : JpaRepository<Service, UUID> {
    fun findByNameAndType(name: String, type: ServiceType): Service?
    fun findAllByType(type: ServiceType): List<Service>

    @Modifying
    @Query(
        value = "DELETE FROM service_oh_group WHERE service_id = :serviceId",
        nativeQuery = true
    )
    fun deleteServiceGroupLinks(@Param("serviceId") serviceId: UUID)

    @Modifying
    @Query(
        value = """
            INSERT INTO service_oh_group (service_id, group_id)
            VALUES (:serviceId, :groupId)
        """,
        nativeQuery = true
    )
    fun linkOhGroup(@Param("serviceId") serviceId: UUID, @Param("groupId") groupId: UUID)

    @Query(
        value = "SELECT group_id FROM service_oh_group WHERE service_id = :serviceId",
        nativeQuery = true
    )
    fun findOhGroupIds(@Param("serviceId") serviceId: UUID): List<UUID>
}