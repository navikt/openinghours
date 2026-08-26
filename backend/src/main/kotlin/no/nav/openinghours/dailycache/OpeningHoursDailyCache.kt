package no.nav.openinghours.dailycache

import no.nav.openinghours.evaluator.NorwegianPublicHolidays
import no.nav.openinghours.evaluator.OpeningHoursDisplayData
import no.nav.openinghours.evaluator.OpeningHoursEvaluator
import no.nav.openinghours.service.ServiceNameAndGroup
import no.nav.openinghours.service.ServiceService
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

data class ServiceCacheEntry(
    val serviceName: String,
    val displayData: OpeningHoursDisplayData,
)

@Component
class OpeningHoursDailyCache(
    private val serviceService: ServiceService,
    private val evaluator: OpeningHoursEvaluator,
    private val clock: Clock,
    private val norwegianPublicHolidays: NorwegianPublicHolidays,
) {
    // Holds an immutable snapshot. Reads always see a fully-consistent map;
    // populate() builds a brand-new map on the calling thread, then swaps the reference
    // in a single atomic write — no window where the cache is empty or partial.
    private val cacheRef = AtomicReference<Map<UUID, ServiceCacheEntry>>(emptyMap())

    fun populate() {
        val today = LocalDate.now(clock)
        val isRedDay = norwegianPublicHolidays.isPublicHoliday(today)
        val serviceGroups = serviceService.getAllServicesForCache()
        val newMap = serviceGroups.mapValues { (_, nameAndGroup) ->
            // null  → service has no linked OH group   → use default
            // non-null but no rule matches today       → evaluator returns null → use default
            val displayData = nameAndGroup.group?.let { evaluator.getDisplayData(today, it) }
                ?: OpeningHoursEvaluator.DEFAULT_DISPLAY_DATA
            // Promote redDay to true only when today is a public holiday AND the existing
            // flag is still false — the only case that actually requires a new object.
            // All other combinations (isRedDay=false, or displayData.redDay already true)
            // return the existing instance unchanged, preserving DEFAULT_DISPLAY_DATA reuse
            // and avoiding a per-service allocation on every populate() call.
            val finalDisplayData = if (isRedDay && !displayData.redDay) displayData.copy(redDay = true) else displayData
            ServiceCacheEntry(serviceName = nameAndGroup.name, displayData = finalDisplayData)
        }
        cacheRef.set(newMap) // atomic swap — readers never observe a partial state
    }

    fun getAll(): Map<UUID, ServiceCacheEntry> = cacheRef.get()

    fun getForService(serviceId: UUID): ServiceCacheEntry? = cacheRef.get()[serviceId]
}