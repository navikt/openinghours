package no.nav.openinghours.repository

import jakarta.transaction.Transactional
import no.nav.openinghours.model.db.OhGroup
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.time.Instant
import java.util.UUID

interface OhGroupRepository : JpaRepository<OhGroup, UUID> {

    @Transactional
    @Modifying
    @Query(
        value = """
            INSERT INTO oh_group (id, name, created_at, updated_at)
            VALUES (:id, :name, :createdAt, :updatedAt)
            ON CONFLICT (id)
            DO UPDATE SET name = EXCLUDED.name, updated_at = EXCLUDED.updated_at
        """,
        nativeQuery = true
    )
    fun upsertGroup(id: UUID, name: String, createdAt: Instant, updatedAt: Instant?)
}