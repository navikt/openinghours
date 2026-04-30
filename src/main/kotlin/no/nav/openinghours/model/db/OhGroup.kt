package no.nav.openinghours.model.db

import jakarta.persistence.*
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.*

@Entity
@Table(name = "oh_group")
class OhGroup(
    @Id
    @Column(nullable = false)
    var id: UUID = UUID.randomUUID(),

    @Column(nullable = false, length = 100)
    @NotBlank(message = "Name is required")
    @Size(max = 100, message = "Name must not exceed 100 characters")
    var name: String = "",

    @ElementCollection
    @CollectionTable(
        name = "oh_group_rule_group_ids",
        joinColumns = [JoinColumn(name = "oh_group_id")]
    )
    @Column(name = "rule_group_id")
    var ruleGroupIds: List<String>? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),

    @Column(name = "updated_at")
    var updatedAt: Instant? = null
)
