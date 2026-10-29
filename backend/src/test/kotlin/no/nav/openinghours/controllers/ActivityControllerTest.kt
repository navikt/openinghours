package no.nav.openinghours.controllers

import no.nav.openinghours.service.ActivityInput
import no.nav.openinghours.service.ActivityService
import no.nav.openinghours.service.ActivityView
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.UUID

@WebMvcTest(ActivityController::class)
@ActiveProfiles("mock")
@AutoConfigureMockMvc(addFilters = false)
class ActivityControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var service: ActivityService

    private val serviceId = UUID.randomUUID()
    private val otherServiceId = UUID.randomUUID()

    private fun view(header: String = "Maintenance", serviceIds: List<UUID> = listOf(serviceId)) = ActivityView(
        id = UUID.randomUUID(),
        serviceIds = serviceIds,
        header = header,
        message = "Database upgrade",
        start = OffsetDateTime.parse("2030-06-10T08:00:00+02:00"),
        end = OffsetDateTime.parse("2030-06-10T09:00:00+02:00"),
        onlyShowForNavEmployees = false,
        ongoing = false
    )

    @Test
    fun `GET today returns activities for service`() {
        `when`(service.getTodayForService(serviceId)).thenReturn(listOf(view("A"), view("B")))

        mockMvc.get("/api/openinghours/activity/service/$serviceId/today").andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(2) }
            jsonPath("$[0].header") { value("A") }
            jsonPath("$[0].serviceIds[0]") { value(serviceId.toString()) }
            jsonPath("$[0].start") { value("2030-06-10T08:00:00+02:00") }
        }
    }

    @Test
    fun `GET service on date`() {
        val date = LocalDate.of(2030, 6, 10)
        `when`(service.getForService(serviceId, date, date)).thenReturn(listOf(view()))

        mockMvc.get("/api/openinghours/activity/service/$serviceId?date=2030-06-10").andExpect {
            status { isOk() }
            jsonPath("$[0].message") { value("Database upgrade") }
        }
    }

    @Test
    fun `GET service range`() {
        val from = LocalDate.of(2030, 6, 1)
        val to = LocalDate.of(2030, 6, 30)
        `when`(service.getForService(serviceId, from, to)).thenReturn(listOf(view(), view()))

        mockMvc.get("/api/openinghours/activity/service/$serviceId/range?from=2030-06-01&to=2030-06-30").andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(2) }
        }
    }

    @Test
    fun `GET all services on date and range`() {
        val date = LocalDate.of(2030, 6, 10)
        `when`(service.getForAllServices(date, date)).thenReturn(listOf(view()))
        `when`(service.getForAllServices(date, date.plusDays(2))).thenReturn(listOf(view(), view(), view()))

        mockMvc.get("/api/openinghours/activity/date?date=2030-06-10").andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(1) }
        }
        mockMvc.get("/api/openinghours/activity/range?from=2030-06-10&to=2030-06-12").andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(3) }
        }
    }

    @Test
    fun `GET range propagates bad request`() {
        val from = LocalDate.of(2030, 6, 10)
        val to = LocalDate.of(2030, 6, 1)
        `when`(service.getForAllServices(from, to))
            .thenThrow(ResponseStatusException(HttpStatus.BAD_REQUEST, "'from' date must not be after 'to' date"))

        mockMvc.get("/api/openinghours/activity/range?from=2030-06-10&to=2030-06-01").andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    fun `POST creates activity covering several services`() {
        val input = ActivityInput(
            setOf(serviceId, otherServiceId), "Maintenance", "Database upgrade",
            LocalDateTime.of(2030, 6, 10, 8, 0), LocalDateTime.of(2030, 6, 10, 9, 0)
        )
        `when`(service.create(input)).thenReturn(view(serviceIds = listOf(serviceId, otherServiceId)))

        mockMvc.post("/api/openinghours/activity") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"serviceIds":["$serviceId","$otherServiceId"],"header":"Maintenance",
                "message":"Database upgrade","start":"2030-06-10T08:00","end":"2030-06-10T09:00"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.serviceIds.length()") { value(2) }
        }
    }

    @Test
    fun `POST rejects blank header and empty service list`() {
        mockMvc.post("/api/openinghours/activity") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"serviceIds":["$serviceId"],"header":" ","message":"m",
                "start":"2030-06-10T08:00","end":"2030-06-10T09:00"}"""
        }.andExpect { status { isBadRequest() } }

        mockMvc.post("/api/openinghours/activity") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"serviceIds":[],"header":"h","message":"m",
                "start":"2030-06-10T08:00","end":"2030-06-10T09:00"}"""
        }.andExpect { status { isBadRequest() } }
    }

    @Test
    fun `POST batch creates several activities`() {
        val inputs = listOf(
            ActivityInput(setOf(serviceId), "A", "m", LocalDateTime.of(2030, 6, 10, 8, 0), LocalDateTime.of(2030, 6, 10, 9, 0)),
            ActivityInput(setOf(serviceId), "B", "m", LocalDateTime.of(2030, 6, 10, 10, 0), LocalDateTime.of(2030, 6, 10, 11, 0))
        )
        `when`(service.createAll(inputs)).thenReturn(listOf(view("A"), view("B")))

        mockMvc.post("/api/openinghours/activity/batch") {
            contentType = MediaType.APPLICATION_JSON
            content = """[
                {"serviceIds":["$serviceId"],"header":"A","message":"m","start":"2030-06-10T08:00","end":"2030-06-10T09:00"},
                {"serviceIds":["$serviceId"],"header":"B","message":"m","start":"2030-06-10T10:00","end":"2030-06-10T11:00"}
            ]"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(2) }
        }
    }

    @Test
    fun `DELETE activity`() {
        val id = UUID.randomUUID()
        `when`(service.delete(id)).thenReturn(true)

        mockMvc.delete("/api/openinghours/activity/$id").andExpect {
            status { isOk() }
            content { string("true") }
        }
    }
}
