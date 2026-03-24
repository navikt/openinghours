package no.nav.openinghours.service

import no.nav.openinghours.model.db.UserDefaultProject
import no.nav.openinghours.model.db.UserDefaultProjectRepository
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException

@Service
class UserDefaultProjectService(
    private val repo: UserDefaultProjectRepository
) {
    private val log = LoggerFactory.getLogger(UserDefaultProjectService::class.java)

    fun upsert(userId: String, projectKey: String, projectName:String): UserDefaultProject {
        if (userId.isBlank() || projectKey.isBlank() || projectName.isBlank())
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "userId and projectKey must be provided")

        return try {
            val normalizedUserId = userId.lowercase()
            val entity = repo.findByUserId(normalizedUserId)
                ?.apply { this.projectKey = projectKey }
                ?: UserDefaultProject(userId = normalizedUserId, projectKey = projectKey, projectName = projectName)

            val saved = repo.save(entity)
            log.info("Upsert default project ok userId={} projectKey={}", userId, projectKey)
            saved
        } catch (e: Exception) {
            log.error("Upsert default project failed userId={} projectKey={} msg={}", userId, projectKey, e.message, e)
            throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Upsert default project: ${e.message}", e)
        }
    }

    fun get(userId: String): UserDefaultProject? =
        try {
            repo.findByUserId(userId.lowercase())
        } catch (e: Exception) {
            log.error("Fetch default project failed userId={} msg={}", userId, e.message, e)
            throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Fetch default project: ${e.message}", e)
        }
}
