package no.nav.openinghours.dailycache

import no.nav.openinghours.evaluator.OpeningHoursDisplayData
import no.nav.openinghours.evaluator.OpeningHoursEvaluator
import no.nav.openinghours.evaluator.ResolvedGroup
import no.nav.openinghours.service.ServiceService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers
import org.mockito.BDDMockito.given
import org.mockito.Mockito
import java.util.UUID

// ── Kotlin-safe Mockito argument matcher helpers ──────────────────────────────
// ArgumentMatchers methods return null at runtime to register the matcher;
// the unchecked cast is intentional and safe inside Mockito stubbing calls.
@Suppress("UNCHECKED_CAST")
private fun <T> any(): T = ArgumentMatchers.any<T>() ?: null as T
private fun <T> eq(obj: T): T = ArgumentMatchers.eq(obj) ?: obj

class OpeningHoursDailyCacheTest {

    private val serviceService: ServiceService = Mockito.mock(ServiceService::class.java)
    private val evaluator: OpeningHoursEvaluator = Mockito.mock(OpeningHoursEvaluator::class.java)
    private val cache = OpeningHoursDailyCache(serviceService, evaluator)

    private val serviceId1 = UUID.randomUUID()
    private val serviceId2 = UUID.randomUUID()

    private val group1 = ResolvedGroup("group-one", emptyList())
    private val group2 = ResolvedGroup("group-two", emptyList())

    private val displayData1 = OpeningHoursDisplayData(
        ruleName = "Weekday rule",
        rule = "??.??.???? ? 1-5 08:00-16:00",
        openingHours = "08:00-16:00",
    )

    @BeforeEach
    fun reset() {
        // Ensure each test starts with a clean cache
        given(serviceService.getAllServicesWithOpeningHours())
            .willReturn(emptyMap<UUID, ResolvedGroup>())
        cache.populate()
    }

    // ------------------------------------------------------------------ //
    // populate() fills the map with correct display data for today        //
    // ------------------------------------------------------------------ //

    @Test
    fun `populate fills cache with evaluator result for each service`() {
        given(serviceService.getAllServicesWithOpeningHours())
            .willReturn(mapOf(serviceId1 to group1))
        given(evaluator.getDisplayData(any(), eq(group1)))
            .willReturn(displayData1)

        cache.populate()

        val result = cache.getForService(serviceId1)
        assertThat(result).isEqualTo(displayData1)
        assertThat(result?.ruleName).isEqualTo("Weekday rule")
        assertThat(result?.openingHours).isEqualTo("08:00-16:00")
    }

    @Test
    fun `populate stores entries for all services returned by serviceService`() {
        val data2 = OpeningHoursDisplayData(ruleName = "Weekend rule", openingHours = "10:00-14:00")
        given(serviceService.getAllServicesWithOpeningHours())
            .willReturn(mapOf(serviceId1 to group1, serviceId2 to group2))
        given(evaluator.getDisplayData(any(), eq(group1))).willReturn(displayData1)
        given(evaluator.getDisplayData(any(), eq(group2))).willReturn(data2)

        cache.populate()

        assertThat(cache.getAll()).containsKeys(serviceId1, serviceId2)
        assertThat(cache.getForService(serviceId1)).isEqualTo(displayData1)
        assertThat(cache.getForService(serviceId2)).isEqualTo(data2)
    }

    // ------------------------------------------------------------------ //
    // populate() replaces stale entries on refresh                        //
    // ------------------------------------------------------------------ //

    @Test
    fun `populate replaces stale entry with updated display data`() {
        val staleData = OpeningHoursDisplayData(ruleName = "Old rule", openingHours = "06:00-12:00")
        val freshData = OpeningHoursDisplayData(ruleName = "New rule", openingHours = "08:00-20:00")

        // First populate – stale data
        given(serviceService.getAllServicesWithOpeningHours())
            .willReturn(mapOf(serviceId1 to group1))
        given(evaluator.getDisplayData(any(), eq(group1))).willReturn(staleData)
        cache.populate()
        assertThat(cache.getForService(serviceId1)?.ruleName).isEqualTo("Old rule")

        // Second populate – fresh data
        given(evaluator.getDisplayData(any(), eq(group1))).willReturn(freshData)
        cache.populate()

        assertThat(cache.getForService(serviceId1)).isEqualTo(freshData)
        assertThat(cache.getForService(serviceId1)?.ruleName).isEqualTo("New rule")
    }

    @Test
    fun `populate removes service that is no longer returned by serviceService`() {
        // First populate – two services
        given(serviceService.getAllServicesWithOpeningHours())
            .willReturn(mapOf(serviceId1 to group1, serviceId2 to group2))
        given(evaluator.getDisplayData(any(), any())).willReturn(displayData1)
        cache.populate()
        assertThat(cache.getAll()).containsKeys(serviceId1, serviceId2)

        // Second populate – only one service remains
        given(serviceService.getAllServicesWithOpeningHours())
            .willReturn(mapOf(serviceId1 to group1))
        cache.populate()

        assertThat(cache.getAll()).containsKey(serviceId1)
        assertThat(cache.getForService(serviceId2)).isNull()
    }

    // ------------------------------------------------------------------ //
    // getForService() returns null for unknown IDs                        //
    // ------------------------------------------------------------------ //

    @Test
    fun `getForService returns null when id is not in cache`() {
        val unknownId = UUID.randomUUID()
        assertThat(cache.getForService(unknownId)).isNull()
    }

    @Test
    fun `getForService returns null after cache is cleared by populate with empty map`() {
        given(serviceService.getAllServicesWithOpeningHours())
            .willReturn(mapOf(serviceId1 to group1))
        given(evaluator.getDisplayData(any(), eq(group1))).willReturn(displayData1)
        cache.populate()
        assertThat(cache.getForService(serviceId1)).isNotNull

        // Repopulate with empty service list – cache should be wiped
        given(serviceService.getAllServicesWithOpeningHours())
            .willReturn(emptyMap<UUID, ResolvedGroup>())
        cache.populate()

        assertThat(cache.getForService(serviceId1)).isNull()
    }

    // ------------------------------------------------------------------ //
    // Services with no matching rule get a sensible default               //
    // ------------------------------------------------------------------ //

    @Test
    fun `populate stores default display data when evaluator returns null`() {
        given(serviceService.getAllServicesWithOpeningHours())
            .willReturn(mapOf(serviceId1 to group1))
        given(evaluator.getDisplayData(any(), eq(group1))).willReturn(null)

        cache.populate()

        val result = cache.getForService(serviceId1)
        assertThat(result).isNotNull
        assertThat(result?.ruleName).isEqualTo("No match")
        assertThat(result?.openingHours).isEqualTo("00:00-23:59")
    }

    @Test
    fun `default fallback is used only for services whose evaluator result is null`() {
        given(serviceService.getAllServicesWithOpeningHours())
            .willReturn(mapOf(serviceId1 to group1, serviceId2 to group2))
        given(evaluator.getDisplayData(any(), eq(group1))).willReturn(displayData1)
        given(evaluator.getDisplayData(any(), eq(group2))).willReturn(null)

        cache.populate()

        assertThat(cache.getForService(serviceId1)).isEqualTo(displayData1)
        val fallback = cache.getForService(serviceId2)
        assertThat(fallback?.ruleName).isEqualTo("No match")
        assertThat(fallback?.openingHours).isEqualTo("00:00-23:59")
    }
}
