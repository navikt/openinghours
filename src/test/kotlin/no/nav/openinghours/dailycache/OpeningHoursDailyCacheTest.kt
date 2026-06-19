package no.nav.openinghours.dailycache

import no.nav.openinghours.evaluator.NorwegianPublicHolidays
import no.nav.openinghours.evaluator.OpeningHoursDisplayData
import no.nav.openinghours.evaluator.OpeningHoursEvaluator
import no.nav.openinghours.evaluator.ResolvedGroup
import no.nav.openinghours.service.ServiceNameAndGroup
import no.nav.openinghours.service.ServiceService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers
import org.mockito.BDDMockito.given
import org.mockito.Mockito
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

// -- Kotlin-safe Mockito argument matcher helpers --------------------------------
// ArgumentMatchers methods return null at runtime to register the matcher;
// the unchecked cast is intentional and safe inside Mockito stubbing calls.
@Suppress("UNCHECKED_CAST")
private fun <T> any(): T = ArgumentMatchers.any<T>() ?: null as T
private fun <T> eq(obj: T): T = ArgumentMatchers.eq(obj) ?: obj

class OpeningHoursDailyCacheTest {

    private val serviceService: ServiceService = Mockito.mock(ServiceService::class.java)
    private val evaluator: OpeningHoursEvaluator = Mockito.mock(OpeningHoursEvaluator::class.java)
    // Fixed clock at a known instant so LocalDate.now(clock) is deterministic in tests.
    // 2024-01-15 is NOT a Norwegian public holiday, so redDay logic leaves existing assertions intact.
    private val clock: Clock = Clock.fixed(Instant.parse("2024-01-15T10:00:00Z"), ZoneOffset.UTC)
    private val cache = OpeningHoursDailyCache(serviceService, evaluator, clock, NorwegianPublicHolidays())

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
            .willReturn(emptyMap<UUID, ServiceNameAndGroup>())
        cache.populate()
    }

    // ------------------------------------------------------------------ //
    // populate() fills the map with correct display data for today        //
    // ------------------------------------------------------------------ //

    @Test
    fun `populate fills cache with evaluator result for each service`() {
        given(serviceService.getAllServicesForCache())
            .willReturn(mapOf(serviceId1 to ServiceNameAndGroup("ServiceA", group1)))
        given(evaluator.getDisplayData(any(), eq(group1)))
            .willReturn(displayData1)

        cache.populate()

        val result = cache.getForService(serviceId1)
        assertThat(result?.displayData).isEqualTo(displayData1)
        assertThat(result?.displayData?.ruleName).isEqualTo("Weekday rule")
        assertThat(result?.displayData?.openingHours).isEqualTo("08:00-16:00")
    }

    @Test
    fun `populate derives today from the injected clock, not the JVM default zone`() {
        // The fixed clock is 2024-01-15T10:00:00Z (UTC), so LocalDate.now(clock) = 2024-01-15.
        // Verify that exact date is forwarded to the evaluator - not LocalDate.now() which
        // would use the JVM default zone and could differ in non-UTC containers.
        val clockDate = LocalDate.now(clock) // 2024-01-15
        given(serviceService.getAllServicesForCache())
            .willReturn(mapOf(serviceId1 to ServiceNameAndGroup("ServiceA", group1)))
        given(evaluator.getDisplayData(eq(clockDate), eq(group1))).willReturn(displayData1)

        cache.populate()

        Mockito.verify(evaluator).getDisplayData(clockDate, group1)
    }

    @Test
    fun `populate stores entries for all services returned by serviceService`() {
        val data2 = OpeningHoursDisplayData(ruleName = "Weekend rule", openingHours = "10:00-14:00")
        given(serviceService.getAllServicesForCache())
            .willReturn(mapOf(serviceId1 to ServiceNameAndGroup("ServiceA", group1), serviceId2 to ServiceNameAndGroup("ServiceB", group2)))
        given(evaluator.getDisplayData(any(), eq(group1))).willReturn(displayData1)
        given(evaluator.getDisplayData(any(), eq(group2))).willReturn(data2)

        cache.populate()

        assertThat(cache.getAll()).containsKeys(serviceId1, serviceId2)
        assertThat(cache.getForService(serviceId1)?.displayData).isEqualTo(displayData1)
        assertThat(cache.getForService(serviceId2)?.displayData).isEqualTo(data2)
    }

    // ------------------------------------------------------------------ //
    // populate() replaces stale entries on refresh                        //
    // ------------------------------------------------------------------ //

    @Test
    fun `populate replaces stale entry with updated display data`() {
        val staleData = OpeningHoursDisplayData(ruleName = "Old rule", openingHours = "06:00-12:00")
        val freshData = OpeningHoursDisplayData(ruleName = "New rule", openingHours = "08:00-20:00")

        // First populate - stale data
        given(serviceService.getAllServicesForCache())
            .willReturn(mapOf(serviceId1 to ServiceNameAndGroup("ServiceA", group1)))
        given(evaluator.getDisplayData(any(), eq(group1))).willReturn(staleData)
        cache.populate()
        assertThat(cache.getForService(serviceId1)?.displayData?.ruleName).isEqualTo("Old rule")

        // Second populate - fresh data
        given(evaluator.getDisplayData(any(), eq(group1))).willReturn(freshData)
        cache.populate()

        assertThat(cache.getForService(serviceId1)?.displayData).isEqualTo(freshData)
        assertThat(cache.getForService(serviceId1)?.displayData?.ruleName).isEqualTo("New rule")
    }

    @Test
    fun `populate removes service that is no longer returned by serviceService`() {
        given(serviceService.getAllServicesForCache())
            .willReturn(mapOf(serviceId1 to ServiceNameAndGroup("ServiceA", group1), serviceId2 to ServiceNameAndGroup("ServiceB", group2)))
        given(evaluator.getDisplayData(any(), any())).willReturn(displayData1)
        cache.populate()
        assertThat(cache.getAll()).containsKeys(serviceId1, serviceId2)

        given(serviceService.getAllServicesForCache())
            .willReturn(mapOf(serviceId1 to ServiceNameAndGroup("ServiceA", group1)))
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
            .willReturn(mapOf(serviceId1 to ServiceNameAndGroup("ServiceA", group1)))
        given(evaluator.getDisplayData(any(), eq(group1))).willReturn(displayData1)
        cache.populate()
        assertThat(cache.getForService(serviceId1)).isNotNull()

        given(serviceService.getAllServicesForCache())
            .willReturn(emptyMap<UUID, ServiceNameAndGroup>())
        cache.populate()

        assertThat(cache.getForService(serviceId1)).isNull()
    }

    // ------------------------------------------------------------------ //
    // Services with no matching rule get a sensible default               //
    // ------------------------------------------------------------------ //

    @Test
    fun `populate stores default display data when evaluator returns null`() {
        given(serviceService.getAllServicesForCache())
            .willReturn(mapOf(serviceId1 to ServiceNameAndGroup("ServiceA", group1)))
        given(evaluator.getDisplayData(any(), eq(group1))).willReturn(null)

        cache.populate()

        val result = cache.getForService(serviceId1)
        assertThat(result).isNotNull()
        assertThat(result?.displayData).isEqualTo(OpeningHoursEvaluator.DEFAULT_DISPLAY_DATA)
        assertThat(result?.displayData?.ruleName).isEqualTo("No Rules stated")
        assertThat(result?.displayData?.openingHours).isEqualTo("00:00-23:59")
        assertThat(result?.displayData?.displayHeader).isEqualTo("Default regel")
    }

    @Test
    fun `default fallback is used only for services whose evaluator result is null`() {
        given(serviceService.getAllServicesForCache())
            .willReturn(mapOf(serviceId1 to ServiceNameAndGroup("ServiceA", group1), serviceId2 to ServiceNameAndGroup("ServiceB", group2)))
        given(evaluator.getDisplayData(any(), eq(group1))).willReturn(displayData1)
        given(evaluator.getDisplayData(any(), eq(group2))).willReturn(null)

        cache.populate()

        assertThat(cache.getForService(serviceId1)?.displayData).isEqualTo(displayData1)
        val fallback = cache.getForService(serviceId2)
        assertThat(fallback?.displayData).isEqualTo(OpeningHoursEvaluator.DEFAULT_DISPLAY_DATA)
        assertThat(fallback?.displayData?.ruleName).isEqualTo("No Rules stated")
        assertThat(fallback?.displayData?.openingHours).isEqualTo("00:00-23:59")
        assertThat(fallback?.displayData?.displayHeader).isEqualTo("Default regel")
    }

    // ------------------------------------------------------------------ //
    // Services with no attached OH group still appear with the default    //
    // ------------------------------------------------------------------ //

    @Test
    fun `populate includes service with no attached group using default display data`() {
        given(serviceService.getAllServicesForCache())
            .willReturn(mapOf(serviceId3 to ServiceNameAndGroup("ServiceC", null)))

        cache.populate()

        val result = cache.getForService(serviceId3)
        assertThat(result).isNotNull()
        assertThat(result?.displayData).isEqualTo(OpeningHoursEvaluator.DEFAULT_DISPLAY_DATA)
        assertThat(result?.displayData?.ruleName).isEqualTo("No Rules stated")
        assertThat(result?.displayData?.openingHours).isEqualTo("00:00-23:59")
        assertThat(result?.displayData?.displayHeader).isEqualTo("Default regel")
    }

    @Test
    fun `populate mixes services with and without groups correctly`() {
        given(serviceService.getAllServicesForCache())
            .willReturn(mapOf(
                serviceId1 to ServiceNameAndGroup("ServiceA", group1),
                serviceId2 to ServiceNameAndGroup("ServiceB", group2),
                serviceId3 to ServiceNameAndGroup("ServiceC", null)
            ))
        given(evaluator.getDisplayData(any(), eq(group1))).willReturn(displayData1)
        given(evaluator.getDisplayData(any(), eq(group2))).willReturn(null)

        cache.populate()

        assertThat(cache.getAll()).containsKeys(serviceId1, serviceId2, serviceId3)
        assertThat(cache.getForService(serviceId1)?.displayData).isEqualTo(displayData1)
        assertThat(cache.getForService(serviceId2)?.displayData).isEqualTo(OpeningHoursEvaluator.DEFAULT_DISPLAY_DATA)
        assertThat(cache.getForService(serviceId3)?.displayData).isEqualTo(OpeningHoursEvaluator.DEFAULT_DISPLAY_DATA)
    }

    @Test
    fun `getForService returns default for service with no group even after multiple populates`() {
        given(serviceService.getAllServicesForCache())
            .willReturn(mapOf(serviceId3 to ServiceNameAndGroup("ServiceC", null)))

        cache.populate()
        cache.populate()

        assertThat(cache.getForService(serviceId3)?.displayData).isEqualTo(OpeningHoursEvaluator.DEFAULT_DISPLAY_DATA)
    }

    // ------------------------------------------------------------------ //
    // redDay is true on Norwegian public holidays                         //
    // ------------------------------------------------------------------ //

    @Test
    fun `populate sets redDay true when today is a Norwegian public holiday`() {
        val easterClock = Clock.fixed(Instant.parse("2024-03-31T10:00:00Z"), ZoneOffset.UTC)
        val cacheOnEaster = OpeningHoursDailyCache(serviceService, evaluator, easterClock, NorwegianPublicHolidays())

        val nonRedData = OpeningHoursDisplayData(ruleName = "Normal rule", openingHours = "08:00-16:00", redDay = false)
        given(serviceService.getAllServicesForCache()).willReturn(mapOf(serviceId1 to ServiceNameAndGroup("ServiceA", group1)))
        given(evaluator.getDisplayData(any(), eq(group1))).willReturn(nonRedData)

        cacheOnEaster.populate()

        assertThat(cacheOnEaster.getForService(serviceId1)?.displayData?.redDay)
            .`as`("redDay must be true on Easter Sunday even if rule does not set it")
            .isTrue()
    }

    @Test
    fun `populate keeps redDay true when both rule and holiday agree`() {
        val easterClock = Clock.fixed(Instant.parse("2024-03-31T10:00:00Z"), ZoneOffset.UTC)
        val cacheOnEaster = OpeningHoursDailyCache(serviceService, evaluator, easterClock, NorwegianPublicHolidays())

        val redByRule = OpeningHoursDisplayData(ruleName = "Holiday rule", openingHours = "00:00-00:00", redDay = true)
        given(serviceService.getAllServicesForCache()).willReturn(mapOf(serviceId1 to ServiceNameAndGroup("ServiceA", group1)))
        given(evaluator.getDisplayData(any(), eq(group1))).willReturn(redByRule)

        cacheOnEaster.populate()

        assertThat(cacheOnEaster.getForService(serviceId1)?.displayData?.redDay).isTrue()
    }

    @Test
    fun `populate sets redDay false on a normal weekday`() {
        val normalData = OpeningHoursDisplayData(ruleName = "Normal rule", openingHours = "08:00-16:00", redDay = false)
        given(serviceService.getAllServicesForCache()).willReturn(mapOf(serviceId1 to ServiceNameAndGroup("ServiceA", group1)))
        given(evaluator.getDisplayData(any(), eq(group1))).willReturn(normalData)

        cache.populate()

        assertThat(cache.getForService(serviceId1)?.displayData?.redDay)
            .`as`("redDay must be false on an ordinary weekday")
            .isFalse()
    }

    @Test
    fun `populate sets redDay true on Constitution Day (17 May)`() {
        val constitutionClock = Clock.fixed(Instant.parse("2024-05-17T08:00:00Z"), ZoneOffset.UTC)
        val cacheOn17May = OpeningHoursDailyCache(serviceService, evaluator, constitutionClock, NorwegianPublicHolidays())

        val normalData = OpeningHoursDisplayData(ruleName = "Rule", openingHours = "00:00-00:00", redDay = false)
        given(serviceService.getAllServicesForCache()).willReturn(mapOf(serviceId1 to ServiceNameAndGroup("ServiceA", group1)))
        given(evaluator.getDisplayData(any(), eq(group1))).willReturn(normalData)

        cacheOn17May.populate()

        assertThat(cacheOn17May.getForService(serviceId1)?.displayData?.redDay)
            .`as`("redDay must be true on Grunnlovsdag (17 May)")
            .isTrue()
    }

    @Test
    fun `populate sets redDay true on default display data when today is a public holiday`() {
        val easterClock = Clock.fixed(Instant.parse("2024-03-31T10:00:00Z"), ZoneOffset.UTC)
        val cacheOnEaster = OpeningHoursDailyCache(serviceService, evaluator, easterClock, NorwegianPublicHolidays())

        given(serviceService.getAllServicesForCache()).willReturn(mapOf(serviceId1 to ServiceNameAndGroup("ServiceA", group1)))
        given(evaluator.getDisplayData(any(), eq(group1))).willReturn(null)

        cacheOnEaster.populate()

        assertThat(cacheOnEaster.getForService(serviceId1)?.displayData?.redDay)
            .`as`("redDay must be true on a public holiday even when the default display data is used")
            .isTrue()
    }

    // ------------------------------------------------------------------ //
    // No unnecessary allocations - identity preserved when no copy needed //
    // ------------------------------------------------------------------ //

    @Test
    fun `populate reuses DEFAULT_DISPLAY_DATA instance on a normal weekday (no copy)`() {
        given(serviceService.getAllServicesForCache()).willReturn(mapOf(serviceId1 to ServiceNameAndGroup("ServiceA", group1)))
        given(evaluator.getDisplayData(any(), eq(group1))).willReturn(null)

        cache.populate()

        assertThat(cache.getForService(serviceId1)?.displayData)
            .`as`("DEFAULT_DISPLAY_DATA singleton must be reused - no copy on a normal weekday")
            .isSameAs(OpeningHoursEvaluator.DEFAULT_DISPLAY_DATA)
    }

    @Test
    fun `populate reuses existing display-data instance when rule already has redDay true on a public holiday`() {
        val easterClock = Clock.fixed(Instant.parse("2024-03-31T10:00:00Z"), ZoneOffset.UTC)
        val cacheOnEaster = OpeningHoursDailyCache(serviceService, evaluator, easterClock, NorwegianPublicHolidays())

        val alreadyRedData = OpeningHoursDisplayData(ruleName = "Holiday rule", openingHours = "00:00-00:00", redDay = true)
        given(serviceService.getAllServicesForCache()).willReturn(mapOf(serviceId1 to ServiceNameAndGroup("ServiceA", group1)))
        given(evaluator.getDisplayData(any(), eq(group1))).willReturn(alreadyRedData)

        cacheOnEaster.populate()

        assertThat(cacheOnEaster.getForService(serviceId1)?.displayData)
            .`as`("Instance must be reused when displayData.redDay is already true â€” no redundant copy")
            .isSameAs(alreadyRedData)
    }

    @Test
    fun `populate reuses non-default display-data instance on a normal weekday`() {
        given(serviceService.getAllServicesForCache()).willReturn(mapOf(serviceId1 to ServiceNameAndGroup("ServiceA", group1)))
        given(evaluator.getDisplayData(any(), eq(group1))).willReturn(displayData1)

        cache.populate()

        assertThat(cache.getForService(serviceId1)?.displayData)
            .`as`("Evaluator result must be reused as-is on a normal weekday â€” no redundant copy")
            .isSameAs(displayData1)
    }

    // ------------------------------------------------------------------ //
    // Atomic swap - readers never see an empty / partially-populated map  //
    // ------------------------------------------------------------------ //

    @RepeatedTest(5)
    fun `concurrent reads during populate never observe an empty map`() {
        given(serviceService.getAllServicesForCache())
            .willReturn(mapOf(serviceId1 to ServiceNameAndGroup("ServiceA", group1)))
        given(evaluator.getDisplayData(any(), eq(group1))).willReturn(displayData1)
        cache.populate()

        val newData = OpeningHoursDisplayData(ruleName = "New rule", openingHours = "09:00-17:00")
        given(serviceService.getAllServicesForCache())
            .willReturn(mapOf(serviceId1 to ServiceNameAndGroup("ServiceA", group1), serviceId2 to ServiceNameAndGroup("ServiceB", group2)))
        given(evaluator.getDisplayData(any(), eq(group1))).willReturn(displayData1)
        given(evaluator.getDisplayData(any(), eq(group2))).willReturn(newData)

        val readerCount = 20
        val errors = CopyOnWriteArrayList<String>()
        val startLatch = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(readerCount + 1)

        executor.submit {
            startLatch.await()
            cache.populate()
        }

        repeat(readerCount) {
            executor.submit {
                startLatch.await()
                val snapshot = cache.getAll()
                if (snapshot.isEmpty()) {
                    errors += "Reader saw an empty map during populate()"
                }
                val sizes = setOf(1, 2)
                if (snapshot.size !in sizes) {
                    errors += "Reader saw unexpected map size ${snapshot.size}"
                }
            }
        }

        startLatch.countDown()
        executor.shutdown()
        val terminated = executor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)
        assertThat(terminated).`as`("Executor should terminate within timeout").isTrue()

        assertThat(errors)
            .`as`("No reader should ever observe an empty or partially-populated cache")
            .isEmpty()
    }
}
