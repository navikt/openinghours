package no.nav.openinghours.service

import no.nav.openinghours.model.db.OpeningHours
import no.nav.openinghours.model.db.OpeningHoursRepository
import no.nav.openinghours.model.db.UserDefaultProject
import no.nav.openinghours.model.db.UserDefaultProjectRepository
import org.slf4j.LoggerFactory
import org.springframework.data.jpa.domain.AbstractPersistable_.id
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.util.*

@Service
class OpeningHoursService(
    private val repo: OpeningHoursRepository
) {
    private val log = LoggerFactory.getLogger(OpeningHoursService::class.java)

    fun upsert(name: String, rule: String): OpeningHours {
        if (name.isBlank() || rule.isBlank())
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "name and rule must be provided")

        return try {
            val entity = repo.findByName(name)
                ?.apply {
                    this.rule = rule
                }
                ?: OpeningHours.create(UUID.randomUUID(), name, rule)

            val saved = repo.save(entity)
            log.info("Upsert opening hours ok name={} rule={}", name, rule)
            saved
        } catch (e: Exception) {
            log.error("Upsert opening hours failed name={} rule={} msg={}", name, rule, e.message, e)
            throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Upsert opening hours: ${e.message}", e)
        }
    }

    fun get(id: UUID): OpeningHours? =
        try {
            repo.findById(id) // Returns OpeningHours
        } catch (e: Exception) {
            log.error("Fetch opening hours failed id={} msg={}", id, e.message, e)
            throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Fetch opening hours: ${e.message}", e)
        }
}
