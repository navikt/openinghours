package no.nav.openinghours.statusplattform

import no.nav.openinghours.dailycache.OpeningHoursDailyCache
import no.nav.openinghours.model.db.ServiceRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

data class SyncResult(
    val fetched: Int,
    val inserted: Int,
    val skippedExisting: Int,
    val skippedInvalid: Int
)

@Service
class StatusplattformSyncService(
    private val client: StatusplattformClient,
    private val repo: ServiceRepository,
    private val cache: OpeningHoursDailyCache
) {
    private val log = LoggerFactory.getLogger(StatusplattformSyncService::class.java)

    companion object {
        const val NAME_MAX = 100
        const val TEAM_MAX = 100
        const val MONITORLINK_MAX = 300
        const val UNKNOWN_TEAM = "ukjent"
    }

    @Synchronized
    fun sync(): SyncResult {
        val remote = client.fetchAll()
        var inserted = 0
        var invalid = 0

        remote.forEach { (type, dto) ->
            val id = dto.id
            val name = dto.name?.trim()
            if (id == null || name.isNullOrEmpty() || name.length > NAME_MAX) {
                log.warn("Skipping invalid Statusplattform service id={} name={} type={}", id, dto.name, type)
                invalid++
                return@forEach
            }
            val team = dto.team?.trim().takeUnless { it.isNullOrEmpty() }?.take(TEAM_MAX) ?: UNKNOWN_TEAM
            val monitorlink = dto.monitorlink?.trim()?.takeIf { it.isNotEmpty() && it.length <= MONITORLINK_MAX }

            if (repo.insertIfAbsent(id, name, type.name, team, monitorlink) > 0) {
                log.info("Added service from Statusplattform id={} name={} type={}", id, name, type)
                inserted++
            }
        }

        val result = SyncResult(
            fetched = remote.size,
            inserted = inserted,
            skippedExisting = remote.size - inserted - invalid,
            skippedInvalid = invalid
        )
        log.info("Statusplattform sync finished {}", result)

        cache.populate()
        return result
    }
}
