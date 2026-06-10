package no.nav.openinghours.dailycache

import no.nav.openinghours.evaluator.OpeningHoursDisplayData
import no.nav.openinghours.evaluator.OpeningHoursEvaluator
import no.nav.openinghours.service.ServiceService
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

@Component
class OpeningHoursDailyCache(
    private val serviceService: ServiceService,
    private val evaluator: OpeningHoursEvaluator,
) {
    // Holds an immutable snapshot. Reads always see a fully-consistent map;
    // populate() builds a brand-new map on the calling thread, then swaps the reference
    // in a single atomic write — no window where the cache is empty or partial.
    private val cacheRef = AtomicReference<Map<UUID, OpeningHoursDisplayData>>(emptyMap())

    fun populate() {
        val today = LocalDate.now()
        val serviceGroups = serviceService.getAllServicesForCache()
        val newMap = serviceGroups.mapValues { (_, group) ->
            // null  → service has no linked OH group   → use default
            // non-null but no rule matches today       → evaluator returns null → use default
            group?.let { evaluator.getDisplayData(today, it) } ?: OpeningHoursEvaluator.DEFAULT_DISPLAY_DATA
        }
        cacheRef.set(newMap) // atomic swap — readers never observe a partial state
    }

    fun getAll(): Map<UUID, OpeningHoursDisplayData> = cacheRef.get()

    fun getForService(serviceId: UUID): OpeningHoursDisplayData? = cacheRef.get()[serviceId]
}