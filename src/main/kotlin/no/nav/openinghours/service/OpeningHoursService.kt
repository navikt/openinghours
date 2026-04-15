package no.nav.openinghours.service

import no.nav.openinghours.model.db.OpeningHours
import no.nav.openinghours.model.db.OpeningHoursRepository
import no.nav.openinghours.validator.OpeningHoursValidator
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.util.*

@Service
class OpeningHoursService(
    private val repo: OpeningHoursRepository,
    private val validator: OpeningHoursValidator

) {
    private val log = LoggerFactory.getLogger(OpeningHoursService::class.java)

    fun upsert(
        name: String,
        rule: String,
        header: String?,
        text: String?,
        onlyShowForNavEmployees: Boolean = false
    ): OpeningHours {
        if (name.isBlank()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Name must not be blank")
        }
        if (rule.isBlank()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Rule must not be blank")
        }
        if (!validator.isAValidRule(rule)) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid rule format")
        }

        val entity = repo.findByName(name)
            ?.apply {
                this.rule = rule
                this.header = header
                this.text = text
                this.onlyShowForNavEmployees = onlyShowForNavEmployees
            }
            ?: OpeningHours.create(
                UUID.randomUUID(),
                name,
                rule,
                header,
                text,
                onlyShowForNavEmployees
            )

        return repo.save(entity)
    }

    fun get(id: UUID): OpeningHours? =
        try {
            repo.findById(id).orElse(null)
        } catch (e: Exception) {
            log.error("Fetch opening hours failed id={} msg={}", id, e.message, e)
            throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Fetch opening hours: ${e.message}", e)
        }

    fun getAll(): List<OpeningHours> =
        try {
            repo.findAll() // Returns List<OpeningHours>
        } catch (e: Exception) {
            log.error("Fetch all opening hours failed msg={}", e.message, e)
            throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Fetch all opening hours: ${e.message}", e)
        }

    @Transactional
    fun delete(id: UUID): Boolean {
        return try {
            if (repo.existsById(id)) {
                repo.deleteById(id)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            log.error("Delete opening hours failed id={} msg={}", id, e.message, e)
            throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Delete opening hours: ${e.message}", e)
        }
    }

    @Transactional
    fun update(
        id: UUID,
        name: String?,
        rule: String?,
        header: String?,
        text: String?,
        onlyShowForNavEmployees: Boolean? = null
    ): OpeningHours {
        return try {
            val entity = repo.findById(id).orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Opening hours rule not found")
            }.apply {
                if (!name.isNullOrBlank()) this.name = name
                if (!rule.isNullOrBlank()) {
                    if (!validator.isAValidRule(rule)) {
                        throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid rule format")
                    }
                    this.rule = rule
                }
                if (header != null) this.header = header
                if (text != null) this.text = text
                this.onlyShowForNavEmployees = onlyShowForNavEmployees ?: this.onlyShowForNavEmployees
            }

            repo.save(entity)
        } catch (e: Exception) {
            log.error("Update opening hours failed id={} msg={}", id, e.message, e)
            throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Update opening hours: ${e.message}", e)
        }
    }


}
