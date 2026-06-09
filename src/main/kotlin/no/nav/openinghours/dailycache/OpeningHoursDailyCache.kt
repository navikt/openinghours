package no.nav.openinghours.dailycache

import no.nav.openinghours.evaluator.OpeningHoursDisplayData
import no.nav.openinghours.evaluator.OpeningHoursEvaluator
import no.nav.openinghours.service.ServiceService
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Component
class OpeningHoursDailyCache(
    private val serviceService: ServiceService,
    private val evaluator: OpeningHoursEvaluator,
) {
    private val cache = ConcurrentHashMap<UUID, OpeningHoursDisplayData>()

    fun populate() {
        val today = LocalDate.now()
        val serviceGroups = serviceService.getAllServicesWithOpeningHours()
        val newMap = serviceGroups.mapValues { (_, group) ->
            evaluator.getDisplayData(today, group)
                ?: OpeningHoursDisplayData(
                    ruleName = "No match",
                    openingHours = "00:00-23:59",
                )
        }
        cache.clear()
        cache.putAll(newMap)
    }

    fun getAll(): Map<UUID, OpeningHoursDisplayData> = cache

    fun getForService(serviceId: UUID): OpeningHoursDisplayData? = cache[serviceId]
}