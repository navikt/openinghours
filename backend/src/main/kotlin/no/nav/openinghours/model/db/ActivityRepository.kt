package no.nav.openinghours.model.db

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

interface ActivityRepository : JpaRepository<Activity, UUID> {

    /** Activities covering [serviceId] that overlap the half-open interval [from, to). */
    @Query(
        "select a from Activity a where :serviceId member of a.serviceIds " +
            "and a.startAt < :to and a.endAt > :from order by a.startAt, a.endAt"
    )
    fun findOverlappingForService(
        @Param("serviceId") serviceId: UUID,
        @Param("from") from: Instant,
        @Param("to") to: Instant
    ): List<Activity>

    /** Activities for all services that overlap the half-open interval [from, to). */
    @Query("select a from Activity a where a.startAt < :to and a.endAt > :from order by a.startAt, a.endAt")
    fun findOverlapping(@Param("from") from: Instant, @Param("to") to: Instant): List<Activity>

    /**
     * Deletes activities whose only linked service is [serviceId]. Must run before the service
     * itself is deleted; links to other services are removed by the FK cascade afterwards.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        value = """
            DELETE FROM activity a
            WHERE EXISTS (SELECT 1 FROM activity_service s WHERE s.activity_id = a.id AND s.service_id = :serviceId)
              AND NOT EXISTS (SELECT 1 FROM activity_service s WHERE s.activity_id = a.id AND s.service_id <> :serviceId)
        """,
        nativeQuery = true
    )
    fun deleteActivitiesOnlyLinkedTo(@Param("serviceId") serviceId: UUID): Int
}
