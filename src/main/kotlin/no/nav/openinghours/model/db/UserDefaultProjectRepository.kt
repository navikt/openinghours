package no.nav.openinghours.model.db

import org.springframework.data.jpa.repository.JpaRepository

interface UserDefaultProjectRepository : JpaRepository<UserDefaultProject, String> {
    fun findByUserId(userId: String): UserDefaultProject?
}
