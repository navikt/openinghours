package no.nav.openinghours.model.db

import jakarta.persistence.*
import java.time.Instant
import java.util.*

@Entity
@Table(name = "rule_group")
open class RuleGroup(
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", nullable = false, updatable = false)
    val id: UUID = UUID.randomUUID(),

    @Column(name = "name", nullable = false, length = 100)
    var name: String,

    @ElementCollection
    @CollectionTable(name = "rule_group_ids", joinColumns = [JoinColumn(name = "group_id")])
    @Column(name = "rule_group_id")
    var ruleGroupIds: List<UUID>? = null,

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
        ): RuleGroup = RuleGroup(
            id = id,
            name = name,
            ruleGroupIds = ruleGroupIds
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