package no.nav.openinghours.controllers

import no.nav.openinghours.model.db.Service
import no.nav.openinghours.model.db.ServiceType
import no.nav.openinghours.service.ServiceService
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.doNothing
import org.mockito.Mockito.doThrow
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

@WebMvcTest(ServiceController::class)
@ActiveProfiles("mock")
@AutoConfigureMockMvc(addFilters = false)
class ServiceControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockBean
    private lateinit var serviceService: ServiceService

    private fun aService(id: UUID = UUID.randomUUID(), name: String = "Gosys", type: ServiceType = ServiceType.TJENESTE, team: String = "Team X") =
        Service.create(id = id, name = name, type = type, team = team)

    @Test
    fun `POST create service`() {
        val svc = aService()
        `when`(serviceService.save("Gosys", ServiceType.TJENESTE, "Team X", null, null, null, null)).thenReturn(svc)

        mockMvc.post("/api/openinghours/service") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"Gosys","type":"TJENESTE","team":"Team X"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.name") { value("Gosys") }
        }
    }

    @Test
    fun `POST create service with blank name returns 400`() {
        mockMvc.post("/api/openinghours/service") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"","type":"TJENESTE","team":"Team X"}"""
        }.andExpect { status { isBadRequest() } }
    }

    @Test
    fun `POST create service with blank team returns 400`() {
        mockMvc.post("/api/openinghours/service") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"Gosys","type":"TJENESTE","team":""}"""
        }.andExpect { status { isBadRequest() } }
    }

    @Test
    fun `POST create service with duplicate name and type returns 409`() {
        `when`(serviceService.save("Gosys", ServiceType.TJENESTE, "Team X", null, null, null, null))
            .thenThrow(ResponseStatusException(HttpStatus.CONFLICT, "Service with name 'Gosys' and type 'TJENESTE' already exists"))

        mockMvc.post("/api/openinghours/service") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"Gosys","type":"TJENESTE","team":"Team X"}"""
        }.andExpect { status { isConflict() } }
    }

    @Test
    fun `POST create service with non-existent ohGroupId returns 400`() {
        val groupId = UUID.randomUUID()
        `when`(serviceService.save("Gosys", ServiceType.TJENESTE, "Team X", null, null, null, groupId))
            .thenThrow(ResponseStatusException(HttpStatus.BAD_REQUEST, "Opening hours group not found: $groupId"))

        mockMvc.post("/api/openinghours/service") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"Gosys","type":"TJENESTE","team":"Team X","ohGroupId":"$groupId"}"""
        }.andExpect { status { isBadRequest() } }
    }

    @Test
    fun `GET service by id returns service`() {
        val id = UUID.randomUUID()
        val svc = aService(id = id)
        `when`(serviceService.get(id)).thenReturn(svc)

        mockMvc.get("/api/openinghours/service/$id")
            .andExpect {
                status { isOk() }
                jsonPath("$.name") { value("Gosys") }
            }
    }

    @Test
    fun `GET service by id returns 404 when not found`() {
        val id = UUID.randomUUID()
        `when`(serviceService.get(id))
            .thenThrow(ResponseStatusException(HttpStatus.NOT_FOUND, "Service not found: $id"))

        mockMvc.get("/api/openinghours/service/$id")
            .andExpect { status { isNotFound() } }
    }

    @Test
    fun `GET all services returns list`() {
        val services = listOf(aService(name = "S1"), aService(name = "S2"))
        `when`(serviceService.getAll()).thenReturn(services)

        mockMvc.get("/api/openinghours/service")
            .andExpect {
                status { isOk() }
                jsonPath("$.length()") { value(2) }
            }
    }

    @Test
    fun `GET services filtered by type`() {
        val services = listOf(aService(type = ServiceType.TJENESTE))
        `when`(serviceService.getAllByType(ServiceType.TJENESTE)).thenReturn(services)

        mockMvc.get("/api/openinghours/service?type=TJENESTE")
            .andExpect {
                status { isOk() }
                jsonPath("$.length()") { value(1) }
            }
    }

    @Test
    fun `PUT update service`() {
        val id = UUID.randomUUID()
        val updated = aService(id = id, name = "Updated")
        `when`(serviceService.update(id, "Updated", ServiceType.TJENESTE, "Team Y", null, null, null, null)).thenReturn(updated)

        mockMvc.put("/api/openinghours/service/$id") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"Updated","type":"TJENESTE","team":"Team Y"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.name") { value("Updated") }
        }
    }

    @Test
    fun `PUT update non-existent service returns 404`() {
        val id = UUID.randomUUID()
        `when`(serviceService.update(id, "X", ServiceType.TJENESTE, "T", null, null, null, null))
            .thenThrow(ResponseStatusException(HttpStatus.NOT_FOUND, "Service not found: $id"))

        mockMvc.put("/api/openinghours/service/$id") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"X","type":"TJENESTE","team":"T"}"""
        }.andExpect { status { isNotFound() } }
    }

    @Test
    fun `PUT update with duplicate name and type returns 409`() {
        val id = UUID.randomUUID()
        `when`(serviceService.update(id, "Dup", ServiceType.TJENESTE, "Team", null, null, null, null))
            .thenThrow(ResponseStatusException(HttpStatus.CONFLICT, "Service with name 'Dup' and type 'TJENESTE' already exists"))

        mockMvc.put("/api/openinghours/service/$id") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"Dup","type":"TJENESTE","team":"Team"}"""
        }.andExpect { status { isConflict() } }
    }

    @Test
    fun `DELETE service returns true`() {
        val id = UUID.randomUUID()
        `when`(serviceService.delete(id)).thenReturn(true)

        mockMvc.delete("/api/openinghours/service/$id")
            .andExpect {
                status { isOk() }
                jsonPath("$") { value(true) }
            }
    }

    @Test
    fun `DELETE service returns false for unknown id`() {
        val id = UUID.randomUUID()
        `when`(serviceService.delete(id)).thenReturn(false)

        mockMvc.delete("/api/openinghours/service/$id")
            .andExpect {
                status { isOk() }
                jsonPath("$") { value(false) }
            }
    }

    @Test
    fun `PUT set oh-group for service`() {
        val id = UUID.randomUUID()
        val groupId = UUID.randomUUID()
        doNothing().`when`(serviceService).setOhGroup(id, groupId)

        mockMvc.put("/api/openinghours/service/$id/oh-group/$groupId")
            .andExpect { status { isOk() } }
    }

    @Test
    fun `PUT set oh-group non-existent service returns 404`() {
        val id = UUID.randomUUID()
        val groupId = UUID.randomUUID()
        doThrow(ResponseStatusException(HttpStatus.NOT_FOUND, "Service not found: $id"))
            .`when`(serviceService).setOhGroup(id, groupId)

        mockMvc.put("/api/openinghours/service/$id/oh-group/$groupId")
            .andExpect { status { isNotFound() } }
    }

    @Test
    fun `DELETE remove oh-group from service`() {
        val id = UUID.randomUUID()
        doNothing().`when`(serviceService).removeOhGroup(id)

        mockMvc.delete("/api/openinghours/service/$id/oh-group")
            .andExpect { status { isOk() } }
    }

    @Test
    fun `GET oh-groups for service returns list of group ids`() {
        val id = UUID.randomUUID()
        val groupId = UUID.randomUUID()
        `when`(serviceService.getOhGroupIdsForService(id)).thenReturn(listOf(groupId))

        mockMvc.get("/api/openinghours/service/$id/oh-groups")
            .andExpect {
                status { isOk() }
                jsonPath("$.length()") { value(1) }
                jsonPath("$[0]") { value(groupId.toString()) }
            }
    }

    @Test
    fun `GET service with invalid UUID returns 400`() {
        mockMvc.get("/api/openinghours/service/not-a-uuid")
            .andExpect { status { isBadRequest() } }
    }
}

