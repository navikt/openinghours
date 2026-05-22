package no.nav.openinghours.controllers

import no.nav.openinghours.evaluator.OpeningHoursDisplayData
import no.nav.openinghours.service.OpeningHoursLookupService
import no.nav.openinghours.service.ServiceService
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import java.time.LocalDate
import java.util.UUID

@WebMvcTest(QueryController::class)
@ActiveProfiles("mock")
@AutoConfigureMockMvc(addFilters = false)
class QueryControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockBean
    private lateinit var lookupService: OpeningHoursLookupService

    @MockBean
    private lateinit var serviceService: ServiceService


    @Test
    fun `query by service returns opening hours`() {
        val serviceId = UUID.randomUUID()
        val groupId = UUID.randomUUID()
        val date = LocalDate.of(2024, 3, 15)

        `when`(serviceService.getOhGroupIdsForService(serviceId)).thenReturn(listOf(groupId))
        `when`(lookupService.getDisplayData(groupId, date)).thenReturn(
            OpeningHoursDisplayData(openingHours = "07:00-21:00", ruleName = "Standard weekdays", rule = "??.??.???? ? 1-5 07:00-21:00")
        )

        mockMvc.get("/api/openinghours/query/service/$serviceId?date=2024-03-15")
            .andExpect {
                status { isOk() }
                jsonPath("$.isOpen") { value(true) }
                jsonPath("$.openingTime") { value("07:00") }
                jsonPath("$.closingTime") { value("21:00") }
                jsonPath("$.matchedRule.name") { value("Standard weekdays") }
            }
    }

    @Test
    fun `query by service returns 404 when no group assigned`() {
        val serviceId = UUID.randomUUID()
        `when`(serviceService.getOhGroupIdsForService(serviceId)).thenReturn(emptyList())

        mockMvc.get("/api/openinghours/query/service/$serviceId?date=2024-03-15")
            .andExpect { status { isNotFound() } }
    }

    @Test
    fun `query by group returns closed`() {
        val groupId = UUID.randomUUID()
        val date = LocalDate.of(2023, 7, 22)

        `when`(lookupService.getDisplayData(groupId, date)).thenReturn(
            OpeningHoursDisplayData(openingHours = "00:00-00:00", ruleName = null, rule = null)
        )

        mockMvc.get("/api/openinghours/query/group/$groupId?date=2023-07-22")
            .andExpect {
                status { isOk() }
                jsonPath("$.isOpen") { value(false) }
                jsonPath("$.matchedRule") { doesNotExist() }
            }
    }

    @Test
    fun `query range returns multiple days`() {
        val serviceId = UUID.randomUUID()
        val groupId = UUID.randomUUID()

        `when`(serviceService.getOhGroupIdsForService(serviceId)).thenReturn(listOf(groupId))
        `when`(lookupService.getDisplayData(groupId, LocalDate.of(2024, 3, 15))).thenReturn(
            OpeningHoursDisplayData(openingHours = "07:00-21:00", ruleName = "Weekdays", rule = "??.??.???? ? 1-5 07:00-21:00")
        )
        `when`(lookupService.getDisplayData(groupId, LocalDate.of(2024, 3, 16))).thenReturn(
            OpeningHoursDisplayData(openingHours = "00:00-00:00", ruleName = null, rule = null)
        )

        mockMvc.get("/api/openinghours/query/service/$serviceId/range?from=2024-03-15&to=2024-03-16")
            .andExpect {
                status { isOk() }
                jsonPath("$.length()") { value(2) }
                jsonPath("$[0].isOpen") { value(true) }
                jsonPath("$[1].isOpen") { value(false) }
            }
    }
}
