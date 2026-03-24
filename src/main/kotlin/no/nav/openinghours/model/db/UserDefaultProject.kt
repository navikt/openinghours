package no.nav.openinghours.model.db

import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "user_default_project")
open class UserDefaultProject(
    @Id
    @Column(name = "user_id", nullable = false, length = 128)
    val userId: String,

    @Column(name = "project_key", nullable = false, length = 128)
    var projectKey: String,

    @Column(name = "project_name", nullable = false, length = 256)
    var projectName: String,

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
) {
    protected constructor(): this("", "", "")

    @PreUpdate
    fun onUpdate() {
        updatedAt = Instant.now()
    }
}