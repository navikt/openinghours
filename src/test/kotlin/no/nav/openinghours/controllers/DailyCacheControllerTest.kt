package no.nav.openinghours.controllers

import no.nav.openinghours.dailycache.OpeningHoursDailyCache
import no.nav.openinghours.dailycache.OpeningHoursDailyCacheScheduler
import no.nav.openinghours.dailycache.ServiceCacheEntry
import no.nav.openinghours.evaluator.OpeningHoursDisplayData
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

@WebMvcTest(DailyCacheController::class)
@ActiveProfiles("mock")
@AutoConfigureMockMvc(addFilters = false)
class DailyCacheControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var cache: OpeningHoursDailyCache

    @MockitoBean
    private lateinit var scheduler: OpeningHoursDailyCacheScheduler

    @MockitoBean
    private lateinit var clock: Clock

    @BeforeEach
    fun setupClock() {
        // Fix the clock at 10:00 UTC so isOpen assertions are deterministic
        val fixed = Clock.fixed(Instant.parse("2024-01-01T10:00:00Z"), ZoneOffset.UTC)
        `when`(clock.instant()).thenReturn(fixed.instant())
        `when`(clock.zone).thenReturn(fixed.zone)
    }

    // ── GET /api/openinghours/daily ────────────────────────────────────────

    @Test
    fun `GET daily returns full map with all entries`() {
        val id1 = UUID.randomUUID()
        val id2 = UUID.randomUUID()
        `when`(cache.getAll()).thenReturn(
            mapOf(
                id1 to ServiceCacheEntry(
                    serviceName = "Bidrag",
                    displayData = OpeningHoursDisplayData(
                        ruleName = "Weekday rule",
                        rule = "??.??.???? ? 1-5 08:00-16:00",
                        openingHours = "08:00-16:00",
                    )
                ),
                id2 to ServiceCacheEntry(
                    serviceName = "Dagpenger",
                    displayData = OpeningHoursDisplayData(
                        ruleName = "Weekend closed",
                        rule = "??.??.???? ? 6-7 00:00-00:00",
                        openingHours = "00:00-00:00",
                    )
                ),
            )
        )

        mockMvc.get("/api/openinghours/daily")
            .andExpect {
                status { isOk() }
                jsonPath("$['$id1'].serviceName") { value("Bidrag") }
                jsonPath("$['$id1'].ruleName") { value("Weekday rule") }
                jsonPath("$['$id1'].openingHours") { value("08:00-16:00") }
                jsonPath("$['$id1'].isOpen") { value(true) }
                jsonPath("$['$id2'].serviceName") { value("Dagpenger") }
                jsonPath("$['$id2'].ruleName") { value("Weekend closed") }
                jsonPath("$['$id2'].openingHours") { value("00:00-00:00") }
                jsonPath("$['$id2'].isOpen") { value(false) }
            }
    }

    @Test
    fun `GET daily returns empty object when cache is empty`() {
        `when`(cache.getAll()).thenReturn(emptyMap())

        mockMvc.get("/api/openinghours/daily")
            .andExpect {
                status { isOk() }
                jsonPath("$") { isEmpty() }
            }
    }

    @Test
    fun `GET daily returns all display data fields`() {
        val id = UUID.randomUUID()
        `when`(cache.getAll()).thenReturn(
            mapOf(
                id to ServiceCacheEntry(
                    serviceName = "Nav Employees Service",
                    displayData = OpeningHoursDisplayData(
                        ruleName = "Nav employees only",
                        rule = "??.??.???? ? 1-5 09:00-15:00",
                        openingHours = "09:00-15:00",
                        displayHeader = "Intern åpningstid",
                        displayText = "Kun for ansatte",
                        onlyShowForNavEmployees = true,
                        redDay = false,
                    )
                )
            )
        )

        mockMvc.get("/api/openinghours/daily")
            .andExpect {
                status { isOk() }
                jsonPath("$['$id'].serviceName") { value("Nav Employees Service") }
                jsonPath("$['$id'].ruleName") { value("Nav employees only") }
                jsonPath("$['$id'].openingHours") { value("09:00-15:00") }
                jsonPath("$['$id'].displayHeader") { value("Intern åpningstid") }
                jsonPath("$['$id'].displayText") { value("Kun for ansatte") }
                jsonPath("$['$id'].onlyShowForNavEmployees") { value(true) }
                jsonPath("$['$id'].redDay") { value(false) }
                jsonPath("$['$id'].isOpen") { value(true) }
            }
    }

    // ── GET /api/openinghours/daily/{serviceId} ────────────────────────────

    @Test
    fun `GET daily by id returns the cached display data for that service`() {
        val serviceId = UUID.randomUUID()
        `when`(cache.getForService(serviceId)).thenReturn(
            ServiceCacheEntry(
                serviceName = "Standard Service",
                displayData = OpeningHoursDisplayData(
                    ruleName = "Standard weekdays",
                    rule = "??.??.???? ? 1-5 07:00-21:00",
                    openingHours = "07:00-21:00",
                )
            )
        )

        mockMvc.get("/api/openinghours/daily/$serviceId")
            .andExpect {
                status { isOk() }
                jsonPath("$.serviceName") { value("Standard Service") }
                jsonPath("$.ruleName") { value("Standard weekdays") }
                jsonPath("$.openingHours") { value("07:00-21:00") }
                jsonPath("$.rule") { value("??.??.???? ? 1-5 07:00-21:00") }
                jsonPath("$.isOpen") { value(true) }
            }
    }

    @Test
    fun `GET daily by id returns all display data fields`() {
        val serviceId = UUID.randomUUID()
        `when`(cache.getForService(serviceId)).thenReturn(
            ServiceCacheEntry(
                serviceName = "Internal Service",
                displayData = OpeningHoursDisplayData(
                    ruleName = "Internal",
                    rule = "??.??.???? ? 1-5 09:00-15:00",
                    openingHours = "09:00-15:00",
                    displayHeader = "Intern åpningstid",
                    displayText = "Kun for ansatte",
                    onlyShowForNavEmployees = true,
                    redDay = false,
                )
            )
        )

        mockMvc.get("/api/openinghours/daily/$serviceId")
            .andExpect {
                status { isOk() }
                jsonPath("$.serviceName") { value("Internal Service") }
                jsonPath("$.ruleName") { value("Internal") }
                jsonPath("$.openingHours") { value("09:00-15:00") }
                jsonPath("$.displayHeader") { value("Intern åpningstid") }
                jsonPath("$.displayText") { value("Kun for ansatte") }
                jsonPath("$.onlyShowForNavEmployees") { value(true) }
                jsonPath("$.redDay") { value(false) }
                jsonPath("$.isOpen") { value(true) }
            }
    }

    // ── GET /api/openinghours/daily/{unknown} returns 404 ─────────────────

    @Test
    fun `GET daily by unknown id returns 404`() {
        val unknownId = UUID.randomUUID()
        `when`(cache.getForService(unknownId)).thenReturn(null)

        mockMvc.get("/api/openinghours/daily/$unknownId")
            .andExpect { status { isNotFound() } }
    }

    @Test
    fun `GET daily by invalid UUID returns 400`() {
        mockMvc.get("/api/openinghours/daily/not-a-uuid")
            .andExpect { status { isBadRequest() } }
    }

    @Test
    fun `POST refresh triggers scheduler refresh and returns 200`() {
        mockMvc.post("/api/openinghours/daily/refresh")
            .andExpect { status { isOk() } }

        verify(scheduler).refresh()
    }

    // ── redDay: public-holiday flag surfaces through the controller ────────

    @Test
    fun `GET daily returns redDay true when cache holds a public-holiday entry`() {
        val holidayId = UUID.randomUUID()
        `when`(cache.getAll()).thenReturn(
            mapOf(
                holidayId to ServiceCacheEntry(
                    serviceName = "Holiday Service",
                    displayData = OpeningHoursDisplayData(
                        ruleName = "Easter Sunday",
                        rule = "??.??.???? ? ? 00:00-00:00",
                        openingHours = "00:00-00:00",
                        redDay = true,
                    )
                )
            )
        )

        mockMvc.get("/api/openinghours/daily")
            .andExpect {
                status { isOk() }
                jsonPath("$['$holidayId'].redDay") { value(true) }
                jsonPath("$['$holidayId'].isOpen") { value(false) }
            }
    }

    @Test
    fun `GET daily by id returns redDay true when cache holds a public-holiday entry`() {
        val serviceId = UUID.randomUUID()
        `when`(cache.getForService(serviceId)).thenReturn(
            ServiceCacheEntry(
                serviceName = "Christmas Service",
                displayData = OpeningHoursDisplayData(
                    ruleName = "Christmas Day",
                    rule = "25.12.???? ? ? 00:00-00:00",
                    openingHours = "00:00-00:00",
                    redDay = true,
                )
            )
        )

        mockMvc.get("/api/openinghours/daily/$serviceId")
            .andExpect {
                status { isOk() }
                jsonPath("$.ruleName") { value("Christmas Day") }
                jsonPath("$.redDay") { value(true) }
                jsonPath("$.isOpen") { value(false) }
            }
    }

    @Test
    fun `GET daily returns redDay false for an ordinary cached entry`() {
        val serviceId = UUID.randomUUID()
        `when`(cache.getAll()).thenReturn(
            mapOf(
                serviceId to ServiceCacheEntry(
                    serviceName = "Normal Service",
                    displayData = OpeningHoursDisplayData(
                        ruleName = "Weekday rule",
                        rule = "??.??.???? ? 1-5 08:00-16:00",
                        openingHours = "08:00-16:00",
                        redDay = false,
                    )
                )
            )
        )

        mockMvc.get("/api/openinghours/daily")
            .andExpect {
                status { isOk() }
                jsonPath("$['$serviceId'].redDay") { value(false) }
            }
    }

    @Test
    fun `GET daily map contains both redDay true and redDay false entries simultaneously`() {
        val holidayId = UUID.randomUUID()
        val normalId  = UUID.randomUUID()
        `when`(cache.getAll()).thenReturn(
            mapOf(
                holidayId to ServiceCacheEntry(
                    serviceName = "Labour Service",
                    displayData = OpeningHoursDisplayData(
                        ruleName = "Labour Day",
                        rule = "01.05.???? ? ? 00:00-00:00",
                        openingHours = "00:00-00:00",
                        redDay = true,
                    )
                ),
                normalId to ServiceCacheEntry(
                    serviceName = "Standard Service",
                    displayData = OpeningHoursDisplayData(
                        ruleName = "Standard weekday",
                        rule = "??.??.???? ? 1-5 08:00-16:00",
                        openingHours = "08:00-16:00",
                        redDay = false,
                    )
                ),
            )
        )

        mockMvc.get("/api/openinghours/daily")
            .andExpect {
                status { isOk() }
                jsonPath("$['$holidayId'].redDay") { value(true) }
                jsonPath("$['$normalId'].redDay") { value(false) }
            }
    }
}
