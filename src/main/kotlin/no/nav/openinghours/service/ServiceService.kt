package no.nav.openinghours.service

import no.nav.openinghours.model.db.OhGroupRepository
import no.nav.openinghours.model.db.Service
import no.nav.openinghours.model.db.ServiceRepository
import no.nav.openinghours.model.db.ServiceType
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service as SpringService
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.util.UUID
import org.springframework.dao.IncorrectResultSizeDataAccessException

@SpringService
class ServiceService(
        private val repo: ServiceRepository,
        private val ohGroupRepo: OhGroupRepository
) {
    private val log = LoggerFactory.getLogger(ServiceService::class.java)

    @Transactional
    fun save(
            name: String,
            type: ServiceType,
            team: String,
            monitorlink: String? = null,
            logglink: String? = null,
            description: String? = null,
            ohGroupId: UUID? = null
    ): Service {
        if (name.isBlank()) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Name must not be blank")
        if (team.isBlank()) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Team must not be blank")
        if (repo.findByNameAndType(name, type) != null) {
            throw ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Service with name '$name' and type '$type' already exists"
            )
        }
        validateOhGroup(ohGroupId)

        return try {
            val saved = repo.saveAndFlush(Service.create(name = name, type = type, team = team,
                    monitorlink = monitorlink, logglink = logglink, description = description))
            ohGroupId?.let { repo.linkOhGroup(saved.id, it) }
            log.info("Saved service name={} type={}", name, type)
            saved
        } catch (e: DataIntegrityViolationException) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Service with name '$name' and type '$type' already exists")
        } catch (e: ResponseStatusException) {
            throw e
        } catch (e: Exception) {
            log.error("Save service failed name={} msg={}", name, e.message, e)
            throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Save service: ${e.message}", e)
        }
    }

    fun get(id: UUID): Service =
            repo.findById(id).orElseThrow {
        ResponseStatusException(HttpStatus.NOT_FOUND, "Service not found: $id")
    }

    fun getAll(): List<Service> =
            try { repo.findAll() }
        catch (e: Exception) {
        log.error("Fetch all services failed msg={}", e.message, e)
        throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to fetch services", e)
    }

    fun getAllByType(type: ServiceType): List<Service> = repo.findAllByType(type)

    @Transactional
    fun update(
            id: UUID,
            name: String?,
            type: ServiceType?,
            team: String?,
            monitorlink: String?,
            logglink: String?,
            description: String?,
            ohGroupId: UUID?
    ): Service {
        val service = repo.findById(id).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Service not found: $id")
        }

        if (!name.isNullOrBlank()) service.name = name
        if (type != null) service.type = type
        if (!team.isNullOrBlank()) service.team = team
        if (monitorlink != null) service.monitorlink = monitorlink
        if (logglink != null) service.logglink = logglink
        if (description != null) service.description = description

        // Enforce unique (name, type) on update
        try {
            repo.findByNameAndType(service.name, service.type)?.let {
                if (it.id != id) throw ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Service with name '${service.name}' and type '${service.type}' already exists"
                )
            }
        } catch (e: IncorrectResultSizeDataAccessException) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "Service with name '${service.name}' and type '${service.type}' already exists"
            )
        } catch (e: DataIntegrityViolationException) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "Service with name '${service.name}' and type '${service.type}' already exists"
            )
        }

        if (ohGroupId != null) {
            validateOhGroup(ohGroupId)
            repo.deleteServiceGroupLinks(id)
            repo.linkOhGroup(id, ohGroupId)
        }

        return try {
            repo.saveAndFlush(service).also { log.info("Updated service id={}", id) }
        } catch (e: DataIntegrityViolationException) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Service '${service.name}' already exists")
        } catch (e: Exception) {
            log.error("Update service failed id={} msg={}", id, e.message, e)
            throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Update service: ${e.message}", e)
        }
    }

    @Transactional
    fun delete(id: UUID): Boolean {
        if (!repo.existsById(id)) return false
        return try {
            repo.deleteServiceGroupLinks(id)
            repo.deleteById(id)
            log.info("Deleted service id={}", id)
            true
        } catch (e: DataIntegrityViolationException) {
            log.warn("Delete service blocked by dependent data id={} msg={}", id, e.message, e)
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "Cannot delete service $id because it has dependent data",
                e
            )
        } catch (e: Exception) {
            log.error("Delete service failed id={} msg={}", id, e.message, e)
            throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Delete service: ${e.message}", e)
        }
    }

    /** Additional: opening-hours-specific helpers */
    fun getOhGroupIdsForService(id: UUID): List<UUID> = repo.findOhGroupIds(id)

    @Transactional
    fun setOhGroup(serviceId: UUID, groupId: UUID) {
        if (!repo.existsById(serviceId))
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Service not found: $serviceId")
        validateOhGroup(groupId)
        repo.deleteServiceGroupLinks(serviceId)
        repo.linkOhGroup(serviceId, groupId)
    }

    @Transactional
    fun removeOhGroup(serviceId: UUID) {
        if (!repo.existsById(serviceId))
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Service not found: $serviceId")
        repo.deleteServiceGroupLinks(serviceId)
    }

    private fun validateOhGroup(id: UUID?) {
        if (id != null && !ohGroupRepo.existsById(id)) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Opening hours group not found: $id")
        }
    }
}