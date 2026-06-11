package no.nav.openinghours.controllers

import no.nav.openinghours.dailycache.OpeningHoursDailyCache
import no.nav.openinghours.evaluator.OpeningHoursDisplayData
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import java.util.UUID

@WebMvcTest(DailyCacheController::class)
@ActiveProfiles("mock")
@AutoConfigureMockMvc(addFilters = false)
class DailyCacheControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var cache: OpeningHoursDailyCache

    // ── GET /api/openinghours/daily ────────────────────────────────────────

    @Test
    fun `GET daily returns full map with all entries`() {
        val id1 = UUID.randomUUID()
        val id2 = UUID.randomUUID()
        `when`(cache.getAll()).thenReturn(
            mapOf(
                id1 to OpeningHoursDisplayData(
                    ruleName = "Weekday rule",
                    rule = "??.??.???? ? 1-5 08:00-16:00",
                    openingHours = "08:00-16:00",
                ),
                id2 to OpeningHoursDisplayData(
                    ruleName = "Weekend closed",
                    rule = "??.??.???? ? 6-7 00:00-00:00",
                    openingHours = "00:00-00:00",
                ),
            )
        )

        mockMvc.get("/api/openinghours/daily")
            .andExpect {
                status { isOk() }
                jsonPath("$['$id1'].ruleName") { value("Weekday rule") }
                jsonPath("$['$id1'].openingHours") { value("08:00-16:00") }
                jsonPath("$['$id2'].ruleName") { value("Weekend closed") }
                jsonPath("$['$id2'].openingHours") { value("00:00-00:00") }
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
                id to OpeningHoursDisplayData(
                    ruleName = "Nav employees only",
                    rule = "??.??.???? ? 1-5 09:00-15:00",
                    openingHours = "09:00-15:00",
                    displayHeader = "Intern åpningstid",
                    displayText = "Kun for ansatte",
                    onlyShowForNavEmployees = true,
                )
            )
        )

        mockMvc.get("/api/openinghours/daily")
            .andExpect {
                status { isOk() }
                jsonPath("$['$id'].ruleName") { value("Nav employees only") }
                jsonPath("$['$id'].openingHours") { value("09:00-15:00") }
                jsonPath("$['$id'].displayHeader") { value("Intern åpningstid") }
                jsonPath("$['$id'].displayText") { value("Kun for ansatte") }
                jsonPath("$['$id'].onlyShowForNavEmployees") { value(true) }
            }
    }

    // ── GET /api/openinghours/daily/{serviceId} ────────────────────────────

    @Test
    fun `GET daily by id returns the cached display data for that service`() {
        val serviceId = UUID.randomUUID()
        val displayData = OpeningHoursDisplayData(
            ruleName = "Standard weekdays",
            rule = "??.??.???? ? 1-5 07:00-21:00",
            openingHours = "07:00-21:00",
        )
        `when`(cache.getForService(serviceId)).thenReturn(displayData)

        mockMvc.get("/api/openinghours/daily/$serviceId")
            .andExpect {
                status { isOk() }
                jsonPath("$.ruleName") { value("Standard weekdays") }
                jsonPath("$.openingHours") { value("07:00-21:00") }
                jsonPath("$.rule") { value("??.??.???? ? 1-5 07:00-21:00") }
            }
    }

    @Test
    fun `GET daily by id returns all display data fields`() {
        val serviceId = UUID.randomUUID()
        `when`(cache.getForService(serviceId)).thenReturn(
            OpeningHoursDisplayData(
                ruleName = "Internal",
                rule = "??.??.???? ? 1-5 09:00-15:00",
                openingHours = "09:00-15:00",
                displayHeader = "Intern åpningstid",
                displayText = "Kun for ansatte",
                onlyShowForNavEmployees = true,
            )
        )

        mockMvc.get("/api/openinghours/daily/$serviceId")
            .andExpect {
                status { isOk() }
                jsonPath("$.ruleName") { value("Internal") }
                jsonPath("$.openingHours") { value("09:00-15:00") }
                jsonPath("$.displayHeader") { value("Intern åpningstid") }
                jsonPath("$.displayText") { value("Kun for ansatte") }
                jsonPath("$.onlyShowForNavEmployees") { value(true) }
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
}

