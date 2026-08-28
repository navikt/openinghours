package no.nav.openinghours.model.db

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "oh_group")
open class OhGroup(
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    val id: UUID = UUID.randomUUID(),

    @Column(name = "name", nullable = false, length = 100)
    var name: String,

    // Hibernate 6.4 maps PostgreSQL varchar[] natively as Array<String>
    @Column(name = "rule_group_ids", columnDefinition = "varchar[]")
    var ruleGroupIds: Array<String>? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),

    @Column(name = "updated_at")
    var updatedAt: Instant? = null
) {
    // JPA no-arg constructor requirement
    protected constructor() : this(name = "")

    @get:Transient
    val ruleGroupUuids: List<UUID>
        get() = ruleGroupIds?.map { UUID.fromString(it) } ?: emptyList()

    @PrePersist
    fun onCreate() {
        createdAt = Instant.now()
        updatedAt = null
    }

    @PreUpdate
    fun onUpdate() {
        updatedAt = Instant.now()
    }

    companion object {
        fun create(id: UUID = UUID.randomUUID(), name: String, ruleGroupIds: List<UUID> = emptyList()): OhGroup =
            OhGroup(
                id = id,
                name = name,
                ruleGroupIds = ruleGroupIds.map { it.toString() }.toTypedArray().ifEmpty { null }
            )
    }
}