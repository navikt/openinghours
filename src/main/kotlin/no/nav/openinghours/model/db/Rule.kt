package no.nav.openinghours.model.db

import jakarta.persistence.*
import java.time.Instant
import java.util.*

@Entity
@Table(name = "rule")
open class Rule(
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", nullable = false, updatable = false)
    val id: UUID = UUID.randomUUID(),

    @Column(name = "name", nullable = false, length = 100)
    var name: String,

    @Column(name = "rule", nullable = false, length = 100)
    var rule: String,

    @Column(name = "header", nullable = true, length = 200)
    var header: String? = null,

    @Column(name = "text", nullable = true, length = 200)
    var text: String? = null,

    @Column(name = "only_show_for_nav_employees", nullable = false)
    var onlyShowForNavEmployees: Boolean = false,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = true)
    var updatedAt: Instant? = null
) {
    protected constructor() : this(
        name = "",
        rule = "",
        header = "",
        text = "",
        onlyShowForNavEmployees = false,
        createdAt = Instant.now(),
        updatedAt = null
    )

    companion object {
        @JvmStatic
        fun create(
            id: UUID,
            name: String,
            rule: String,
            header: String?,
            text: String?,
            onlyShowForNavEmployees : Boolean = false
        ): Rule = Rule(
            id = id,
            name = name,
            rule = rule,
            header = header,
            text = text,
            onlyShowForNavEmployees = onlyShowForNavEmployees
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
