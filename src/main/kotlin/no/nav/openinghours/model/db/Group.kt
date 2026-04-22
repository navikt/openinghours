package no.nav.openinghours.model.db

import jakarta.persistence.*
import java.time.Instant
import java.util.*

@Entity
@Table(name = "group")
data class Group(
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", nullable = false, updatable = false)
    val id: UUID = UUID.randomUUID(),

    @Column(name = "name", nullable = false, length = 100)
    var name: String,

    @ElementCollection
    @CollectionTable(name = "rule_group_ids", joinColumns = [JoinColumn(name = "group_id")])
    @Column(name = "rule_group_id")
    var ruleGroupIds: List<UUID> = emptyList(),

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = true)
    var updatedAt: Instant? = null
) {

    protected constructor() : this(
        name = "",
        ruleGroupIds = emptyList(),
        createdAt = Instant.now(),
        updatedAt = null
    )

    companion object {
        @JvmStatic
        fun create(
            id: UUID,
            name: String,
            ruleGroupIds: List<UUID>
        ): Group = Group(
            id = id,
            name = name,
            ruleGroupIds = emptyList()
        )
    }

    @PrePersist
    fun onCreate() {
        createdAt = Instant.now()
        updatedAt = null
    }

    @PreUpdate
    fun onUpdate() {
        updatedAt = Instant.now()
    }
}