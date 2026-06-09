package no.nav.openinghours.dailycache

import no.nav.openinghours.evaluator.OpeningHoursDisplayData
import no.nav.openinghours.evaluator.OpeningHoursEvaluator
import no.nav.openinghours.evaluator.ResolvedGroup
import no.nav.openinghours.service.ServiceService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers
import org.mockito.BDDMockito.given
import org.mockito.Mockito
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

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
    private val serviceId3 = UUID.randomUUID()

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
        given(serviceService.getAllServicesForCache())
            .willReturn(emptyMap<UUID, ResolvedGroup?>())
        cache.populate()
    }

    // ------------------------------------------------------------------ //
    // populate() fills the map with correct display data for today        //
    // ------------------------------------------------------------------ //

    @Test
    fun `populate fills cache with evaluator result for each service`() {
        given(serviceService.getAllServicesForCache())
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
        given(serviceService.getAllServicesForCache())
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
        given(serviceService.getAllServicesForCache())
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
        given(serviceService.getAllServicesForCache())
            .willReturn(mapOf(serviceId1 to group1, serviceId2 to group2))
        given(evaluator.getDisplayData(any(), any())).willReturn(displayData1)
        cache.populate()
        assertThat(cache.getAll()).containsKeys(serviceId1, serviceId2)

        // Second populate – only one service remains
        given(serviceService.getAllServicesForCache())
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
        given(serviceService.getAllServicesForCache())
            .willReturn(mapOf(serviceId1 to group1))
        given(evaluator.getDisplayData(any(), eq(group1))).willReturn(displayData1)
        cache.populate()
        assertThat(cache.getForService(serviceId1)).isNotNull()

        // Repopulate with empty service list – cache should be wiped
        given(serviceService.getAllServicesForCache())
            .willReturn(emptyMap<UUID, ResolvedGroup?>())
        cache.populate()

        assertThat(cache.getForService(serviceId1)).isNull()
    }

    // ------------------------------------------------------------------ //
    // Services with no matching rule get a sensible default               //
    // ------------------------------------------------------------------ //

    @Test
    fun `populate stores default display data when evaluator returns null`() {
        given(serviceService.getAllServicesForCache())
            .willReturn(mapOf(serviceId1 to group1))
        given(evaluator.getDisplayData(any(), eq(group1))).willReturn(null)

        cache.populate()

        val result = cache.getForService(serviceId1)
        assertThat(result).isNotNull()
        assertThat(result).isEqualTo(OpeningHoursEvaluator.DEFAULT_DISPLAY_DATA)
        assertThat(result?.ruleName).isEqualTo("No Rules stated")
        assertThat(result?.openingHours).isEqualTo("00:00-23:59")
        assertThat(result?.displayHeader).isEqualTo("Default regel")
    }

    @Test
    fun `default fallback is used only for services whose evaluator result is null`() {
        given(serviceService.getAllServicesForCache())
            .willReturn(mapOf(serviceId1 to group1, serviceId2 to group2))
        given(evaluator.getDisplayData(any(), eq(group1))).willReturn(displayData1)
        given(evaluator.getDisplayData(any(), eq(group2))).willReturn(null)

        cache.populate()

        assertThat(cache.getForService(serviceId1)).isEqualTo(displayData1)
        val fallback = cache.getForService(serviceId2)
        assertThat(fallback).isEqualTo(OpeningHoursEvaluator.DEFAULT_DISPLAY_DATA)
        assertThat(fallback?.ruleName).isEqualTo("No Rules stated")
        assertThat(fallback?.openingHours).isEqualTo("00:00-23:59")
        assertThat(fallback?.displayHeader).isEqualTo("Default regel")
    }

    // ------------------------------------------------------------------ //
    // Services with no attached OH group still appear with the default    //
    // ------------------------------------------------------------------ //

    @Test
    fun `populate includes service with no attached group using default display data`() {
        given(serviceService.getAllServicesForCache())
            .willReturn(mapOf(serviceId3 to null))

        cache.populate()

        val result = cache.getForService(serviceId3)
        assertThat(result).isNotNull()
        assertThat(result).isEqualTo(OpeningHoursEvaluator.DEFAULT_DISPLAY_DATA)
        assertThat(result?.ruleName).isEqualTo("No Rules stated")
        assertThat(result?.openingHours).isEqualTo("00:00-23:59")
        assertThat(result?.displayHeader).isEqualTo("Default regel")
    }

    @Test
    fun `populate mixes services with and without groups correctly`() {
        // serviceId1 has a group that resolves to displayData1
        // serviceId2 has a group with no matching rule → DEFAULT_DISPLAY_DATA
        // serviceId3 has no group at all → DEFAULT_DISPLAY_DATA
        given(serviceService.getAllServicesForCache())
            .willReturn(mapOf(serviceId1 to group1, serviceId2 to group2, serviceId3 to null))
        given(evaluator.getDisplayData(any(), eq(group1))).willReturn(displayData1)
        given(evaluator.getDisplayData(any(), eq(group2))).willReturn(null)

        cache.populate()

        assertThat(cache.getAll()).containsKeys(serviceId1, serviceId2, serviceId3)
        assertThat(cache.getForService(serviceId1)).isEqualTo(displayData1)
        assertThat(cache.getForService(serviceId2)).isEqualTo(OpeningHoursEvaluator.DEFAULT_DISPLAY_DATA)
        assertThat(cache.getForService(serviceId3)).isEqualTo(OpeningHoursEvaluator.DEFAULT_DISPLAY_DATA)
    }

    @Test
    fun `getForService returns default for service with no group even after multiple populates`() {
        given(serviceService.getAllServicesForCache())
            .willReturn(mapOf(serviceId3 to null))

        cache.populate()
        cache.populate() // second refresh — entry must still be present

        assertThat(cache.getForService(serviceId3)).isEqualTo(OpeningHoursEvaluator.DEFAULT_DISPLAY_DATA)
    }

    // ------------------------------------------------------------------ //
    // Atomic swap — readers never see an empty / partially-populated map  //
    // ------------------------------------------------------------------ //

    @RepeatedTest(5)
    fun `concurrent reads during populate never observe an empty map`() {
        // Pre-populate so there is always an "old" snapshot for readers to see.
        given(serviceService.getAllServicesForCache())
            .willReturn(mapOf(serviceId1 to group1))
        given(evaluator.getDisplayData(any(), eq(group1))).willReturn(displayData1)
        cache.populate()

        // Prepare a richer "new" snapshot that populate() will swap in.
        val newData = OpeningHoursDisplayData(ruleName = "New rule", openingHours = "09:00-17:00")
        given(serviceService.getAllServicesForCache())
            .willReturn(mapOf(serviceId1 to group1, serviceId2 to group2))
        given(evaluator.getDisplayData(any(), eq(group1))).willReturn(displayData1)
        given(evaluator.getDisplayData(any(), eq(group2))).willReturn(newData)

        val readerCount = 20
        val errors = CopyOnWriteArrayList<String>()
        val startLatch = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(readerCount + 1)

        // Writer thread
        executor.submit {
            startLatch.await()
            cache.populate()
        }

        // Reader threads — each snapshot must be non-empty and internally consistent
        repeat(readerCount) {
            executor.submit {
                startLatch.await()
                val snapshot = cache.getAll()
                if (snapshot.isEmpty()) {
                    errors += "Reader saw an empty map during populate()"
                }
                // Either the old map (1 entry) or the new map (2 entries) — never a mix
                val sizes = setOf(1, 2)
                if (snapshot.size !in sizes) {
                    errors += "Reader saw unexpected map size ${snapshot.size}"
                }
            }
        }

        startLatch.countDown()
        executor.shutdown()
        executor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)

        assertThat(errors)
            .`as`("No reader should ever observe an empty or partially-populated cache")
            .isEmpty()
    }
}
