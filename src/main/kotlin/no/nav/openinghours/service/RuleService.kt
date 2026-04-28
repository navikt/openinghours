package no.nav.openinghours.service

import no.nav.openinghours.model.db.Rule
import no.nav.openinghours.model.db.RuleRepository
import no.nav.openinghours.validator.RuleValidator
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.util.*

@Service
class RuleService(
    private val repo: RuleRepository,
    private val validator: RuleValidator

) {
    private val log = LoggerFactory.getLogger(RuleService::class.java)

    fun upsert(
        name: String,
        rule: String,
        header: String?,
        text: String?,
        onlyShowForNavEmployees: Boolean = false
    ): Rule {
        return try {
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
                    this.header = header ?: " " // Default to a single space
                    this.text = text ?: " "     // Default to a single space
                    this.onlyShowForNavEmployees = onlyShowForNavEmployees
                }
                ?: Rule.create(
                    UUID.randomUUID(),
                    name,
                    rule,
                    header ?: " ", // Default to a single space
                    text ?: " ",   // Default to a single space
                    onlyShowForNavEmployees
                )

            repo.save(entity).also {
                log.info("Successfully upserted opening hours rule with name={}", name)
            }
        } catch (e: Exception) {
            log.error("Upsert opening hours failed name={} msg={}", name, e.message, e)
            throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Upsert opening hours: ${e.message}", e)
        }
    }

    fun get(id: UUID): Rule? =
        try {
            repo.findById(id).orElse(null)
        } catch (e: Exception) {
            log.error("Fetch opening hours failed id={} msg={}", id, e.message, e)
            throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Fetch opening hours: ${e.message}", e)
        }

    fun getAll(): List<Rule> =
        try {
            repo.findAll() // Returns List<Rule>
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
    ): Rule {
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
        } catch (e: ResponseStatusException) {
            throw e // Preserve the original HTTP status
        } catch (e: Exception) {
            log.error("Update opening hours failed id={} msg={}", id, e.message, e)
            throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Update opening hours: ${e.message}", e)
        }
    }


}
