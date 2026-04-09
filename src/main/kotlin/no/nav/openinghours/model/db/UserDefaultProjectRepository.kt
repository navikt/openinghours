package no.nav.openinghours.model.db

import org.springframework.data.jpa.repository.JpaRepository

interface UserDefaultProjectRepository : JpaRepository<UserDefaultProject, String> {
    fun findByUserId(id: String): UserDefaultProject?
}
