package no.nav.openinghours.service

import no.nav.openinghours.model.db.Rule
import no.nav.openinghours.model.db.RuleRepository
import no.nav.openinghours.validator.RuleValidator
import org.hibernate.exception.ConstraintViolationException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.sql.SQLException
import java.util.*
import no.nav.openinghours.model.db.OhGroupRepository
import org.springframework.dao.DataIntegrityViolationException

@Service
class RuleService(
    private val repo: RuleRepository,
    private val ohGroupRepo: OhGroupRepository,
    private val validator: RuleValidator
) {
    private val log = LoggerFactory.getLogger(RuleService::class.java)
    private val ruleNameUniqueConstraint = "uq_rule_name"

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

            repo.saveAndFlush(entity).also {
                log.info("Successfully upserted opening hours rule with name={}", name)
            }
        } catch (e: ResponseStatusException) {
            throw e
        } catch (e: DataIntegrityViolationException) {
            if (isRuleNameConflict(e)) {
                throw ResponseStatusException(HttpStatus.CONFLICT, "Rule with name '$name' already exists")
            }
            log.error("Upsert opening hours integrity violation name={} msg={}", name, e.message, e)
            throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Upsert opening hours: ${e.message}", e)
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
            if (!repo.existsById(id)) return false
            val idStr = id.toString()
            val affected = ohGroupRepo.findAllReferencing(idStr)

            affected.forEach { parent ->
                parent.ruleGroupIds = parent.ruleGroupIds
                    ?.filter { it != idStr }
                    ?.toTypedArray()
                    ?.ifEmpty { null }
            }

            ohGroupRepo.saveAll(affected)
            repo.deleteById(id)

            log.info("Deleted rule id={} and removed from {} parent group(s)", id, affected.size)
            true
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

            repo.saveAndFlush(entity)
        } catch (e: ResponseStatusException) {
            throw e // Preserve the original HTTP status
        } catch (e: DataIntegrityViolationException) {
            if (isRuleNameConflict(e)) {
                val conflictName = name ?: id.toString()
                throw ResponseStatusException(HttpStatus.CONFLICT, "Rule with name '$conflictName' already exists")
            }
            log.error("Update opening hours integrity violation id={} msg={}", id, e.message, e)
            throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Update opening hours: ${e.message}", e)
        } catch (e: Exception) {
            log.error("Update opening hours failed id={} msg={}", id, e.message, e)
            throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Update opening hours: ${e.message}", e)
        }

    }

    private fun isRuleNameConflict(exception: DataIntegrityViolationException): Boolean {
        var current: Throwable? = exception
        while (current != null) {
            if (current is ConstraintViolationException &&
                current.constraintName == ruleNameUniqueConstraint
            ) {
                return true
            }
            if (current is SQLException &&
                current.sqlState == "23505" &&
                current.message?.contains(ruleNameUniqueConstraint) == true
            ) {
                return true
            }
            current = current.cause
        }
        return false
    }


}
