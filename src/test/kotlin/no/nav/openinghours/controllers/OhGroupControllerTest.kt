package no.nav.openinghours.controllers

import no.nav.openinghours.model.db.OhGroup
import no.nav.openinghours.model.db.Service
import no.nav.openinghours.model.db.ServiceType
import no.nav.openinghours.service.GroupAssociations
import no.nav.openinghours.service.OhGroupService
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.test.context.bean.override.mockito.MockitoBean
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

@WebMvcTest(OhGroupController::class)
@ActiveProfiles("mock")
@AutoConfigureMockMvc(addFilters = false)
class OhGroupControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var ohGroupService: OhGroupService

    private fun aGroup(id: UUID = UUID.randomUUID(), name: String = "Standard") =
        OhGroup.create(id = id, name = name, ruleGroupIds = emptyList())

    @Test
    fun `POST create group`() {
        val group = aGroup(name = "Standard")
        `when`(ohGroupService.save("Standard", emptyList())).thenReturn(group)

        mockMvc.post("/api/openinghours/group?name=Standard")
            .andExpect {
                status { isOk() }
                jsonPath("$.name") { value("Standard") }
            }
    }

    @Test
    fun `POST create group with blank name returns 400`() {
        `when`(ohGroupService.save("", emptyList()))
            .thenThrow(ResponseStatusException(HttpStatus.BAD_REQUEST, "Name must not be blank"))

        mockMvc.post("/api/openinghours/group?name=") {
            contentType = MediaType.APPLICATION_JSON
        }.andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    fun `POST create group with duplicate name returns 409`() {
        `when`(ohGroupService.save("Duplicate", emptyList()))
            .thenThrow(ResponseStatusException(HttpStatus.CONFLICT, "Group with name 'Duplicate' already exists"))

        mockMvc.post("/api/openinghours/group?name=Duplicate")
            .andExpect { status { isConflict() } }
    }

    @Test
    fun `GET group by id returns group`() {
        val id = UUID.randomUUID()
        val group = aGroup(id = id, name = "Office")
        `when`(ohGroupService.get(id)).thenReturn(group)

        mockMvc.get("/api/openinghours/group/$id")
            .andExpect {
                status { isOk() }
                jsonPath("$.name") { value("Office") }
            }
    }

    @Test
    fun `GET group by id returns 404 when not found`() {
        val id = UUID.randomUUID()
        `when`(ohGroupService.get(id))
            .thenThrow(ResponseStatusException(HttpStatus.NOT_FOUND, "Group not found: $id"))

        mockMvc.get("/api/openinghours/group/$id")
            .andExpect { status { isNotFound() } }
    }

    @Test
    fun `GET all groups returns list`() {
        val groups = listOf(aGroup(name = "G1"), aGroup(name = "G2"))
        `when`(ohGroupService.getAll()).thenReturn(groups)

        mockMvc.get("/api/openinghours/group")
            .andExpect {
                status { isOk() }
                jsonPath("$.length()") { value(2) }
                jsonPath("$[0].name") { value("G1") }
            }
    }

    @Test
    fun `PUT update group`() {
        val id = UUID.randomUUID()
        val ruleId = UUID.randomUUID()
        val updated = aGroup(id = id, name = "Updated")
        `when`(ohGroupService.update(id, "Updated", listOf(ruleId))).thenReturn(updated)

        mockMvc.put("/api/openinghours/group/$id") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"Updated","ruleGroupIds":["$ruleId"]}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.name") { value("Updated") }
        }
    }

    @Test
    fun `PUT update group with empty body retains existing data`() {
        val id = UUID.randomUUID()
        val existing = aGroup(id = id, name = "Unchanged")
        `when`(ohGroupService.update(id, null, null)).thenReturn(existing)

        mockMvc.put("/api/openinghours/group/$id") {
            contentType = MediaType.APPLICATION_JSON
            content = """{}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.name") { value("Unchanged") }
        }
    }

    @Test
    fun `PUT update non-existent group returns 404`() {
        val id = UUID.randomUUID()
        `when`(ohGroupService.update(id, "X", null))
            .thenThrow(ResponseStatusException(HttpStatus.NOT_FOUND, "Group not found: $id"))

        mockMvc.put("/api/openinghours/group/$id") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"X"}"""
        }.andExpect { status { isNotFound() } }
    }

    @Test
    fun `DELETE group returns true`() {
        val id = UUID.randomUUID()
        `when`(ohGroupService.delete(id)).thenReturn(true)

        mockMvc.delete("/api/openinghours/group/$id")
            .andExpect {
                status { isOk() }
                jsonPath("$") { value(true) }
            }
    }

    @Test
    fun `DELETE group returns false for unknown id`() {
        val id = UUID.randomUUID()
        `when`(ohGroupService.delete(id)).thenReturn(false)

        mockMvc.delete("/api/openinghours/group/$id")
            .andExpect {
                status { isOk() }
                jsonPath("$") { value(false) }
            }
    }

    @Test
    fun `DELETE group returns 409 with service and group names when group is in use and confirm is not set`() {
        val id = UUID.randomUUID()
        val svc = Service.create(name = "My Service", type = ServiceType.TJENESTE, team = "Team A")
        val parentGroup = aGroup(name = "Parent Group")
        `when`(ohGroupService.getAssociationsByGroupId(id))
            .thenReturn(GroupAssociations(services = listOf(svc), groups = listOf(parentGroup)))

        mockMvc.delete("/api/openinghours/group/$id")
            .andExpect {
                status { isConflict() }
                jsonPath("$.message") {
                    value("Group is in use by 1 service(s): My Service and 1 group(s): Parent Group. Pass ?confirm=true to delete anyway.")
                }
            }
    }

    @Test
    fun `DELETE group returns 409 listing only services when no parent groups`() {
        val id = UUID.randomUUID()
        val svc = Service.create(name = "Svc A", type = ServiceType.TJENESTE, team = "Team A")
        `when`(ohGroupService.getAssociationsByGroupId(id))
            .thenReturn(GroupAssociations(services = listOf(svc), groups = emptyList()))

        mockMvc.delete("/api/openinghours/group/$id")
            .andExpect {
                status { isConflict() }
                jsonPath("$.message") {
                    value("Group is in use by 1 service(s): Svc A. Pass ?confirm=true to delete anyway.")
                }
            }
    }

    @Test
    fun `DELETE group returns 409 listing only parent groups when no services`() {
        val id = UUID.randomUUID()
        val parentGroup = aGroup(name = "Parent")
        `when`(ohGroupService.getAssociationsByGroupId(id))
            .thenReturn(GroupAssociations(services = emptyList(), groups = listOf(parentGroup)))

        mockMvc.delete("/api/openinghours/group/$id")
            .andExpect {
                status { isConflict() }
                jsonPath("$.message") {
                    value("Group is in use by 1 group(s): Parent. Pass ?confirm=true to delete anyway.")
                }
            }
    }

    @Test
    fun `DELETE group with confirm=true deletes even when group is in use`() {
        val id = UUID.randomUUID()
        `when`(ohGroupService.delete(id)).thenReturn(true)

        mockMvc.delete("/api/openinghours/group/$id") {
            param("confirm", "true")
        }.andExpect {
            status { isOk() }
            jsonPath("$") { value(true) }
        }
    }

    @Test
    fun `GET group for service returns linked group`() {
        val serviceId = UUID.randomUUID()
        val group = aGroup(name = "Linked")
        `when`(ohGroupService.getOhGroupForService(serviceId)).thenReturn(group)

        mockMvc.get("/api/openinghours/group/service/$serviceId")
            .andExpect {
                status { isOk() }
                jsonPath("$.name") { value("Linked") }
            }
    }

    @Test
    fun `GET group for service returns 404 when no group linked`() {
        val serviceId = UUID.randomUUID()
        `when`(ohGroupService.getOhGroupForService(serviceId))
            .thenThrow(ResponseStatusException(HttpStatus.NOT_FOUND, "No opening-hours group linked to service id $serviceId"))

        mockMvc.get("/api/openinghours/group/service/$serviceId")
            .andExpect { status { isNotFound() } }
    }

    @Test
    fun `GET associations returns services and groups`() {
        val groupId = UUID.randomUUID()
        val service = Service.create(name = "My Service", type = ServiceType.TJENESTE, team = "Team A")
        val childGroup = aGroup(name = "Child Group")
        val associations = GroupAssociations(services = listOf(service), groups = listOf(childGroup))
        `when`(ohGroupService.getAssociationsByGroupId(groupId)).thenReturn(associations)

        mockMvc.get("/api/openinghours/group/$groupId/associations")
            .andExpect {
                status { isOk() }
                jsonPath("$.services.length()") { value(1) }
                jsonPath("$.services[0].name") { value("My Service") }
                jsonPath("$.groups.length()") { value(1) }
                jsonPath("$.groups[0].name") { value("Child Group") }
            }
    }

    @Test
    fun `GET associations returns 404 when group does not exist`() {
        val groupId = UUID.randomUUID()
        `when`(ohGroupService.getAssociationsByGroupId(groupId))
            .thenThrow(ResponseStatusException(HttpStatus.NOT_FOUND, "Group not found: $groupId"))

        mockMvc.get("/api/openinghours/group/$groupId/associations")
            .andExpect {
                status { isNotFound() }
                jsonPath("$.message") { value("Group not found: $groupId") }
            }
    }

    @Test
    fun `GET associations returns 200 with empty lists when group exists but has no associations`() {
        val groupId = UUID.randomUUID()
        `when`(ohGroupService.getAssociationsByGroupId(groupId))
            .thenReturn(GroupAssociations(emptyList(), emptyList()))

        mockMvc.get("/api/openinghours/group/$groupId/associations")
            .andExpect {
                status { isOk() }
                jsonPath("$.services.length()") { value(0) }
                jsonPath("$.groups.length()") { value(0) }
            }
    }

    @Test
    fun `DELETE rule from group returns updated group`() {
        val groupId = UUID.randomUUID()
        val ruleId = UUID.randomUUID()
        val updatedGroup = aGroup(id = groupId, name = "My Group")
        `when`(ohGroupService.removeRuleFromGroup(groupId, ruleId)).thenReturn(updatedGroup)

        mockMvc.delete("/api/openinghours/group/$groupId/rules/$ruleId")
            .andExpect {
                status { isOk() }
                jsonPath("$.name") { value("My Group") }
            }
    }

    @Test
    fun `DELETE rule from group returns 404 when rule is not a member of the group`() {
        val groupId = UUID.randomUUID()
        val ruleId = UUID.randomUUID()
        `when`(ohGroupService.removeRuleFromGroup(groupId, ruleId))
            .thenThrow(ResponseStatusException(HttpStatus.NOT_FOUND, "Rule $ruleId is not a member of group $groupId"))

        mockMvc.delete("/api/openinghours/group/$groupId/rules/$ruleId")
            .andExpect {
                status { isNotFound() }
                jsonPath("$.message") { value("Rule $ruleId is not a member of group $groupId") }
            }
    }

    @Test
    fun `DELETE rule from group returns 404 when group does not exist`() {
        val groupId = UUID.randomUUID()
        val ruleId = UUID.randomUUID()
        `when`(ohGroupService.removeRuleFromGroup(groupId, ruleId))
            .thenThrow(ResponseStatusException(HttpStatus.NOT_FOUND, "Group not found: $groupId"))

        mockMvc.delete("/api/openinghours/group/$groupId/rules/$ruleId")
            .andExpect {
                status { isNotFound() }
                jsonPath("$.message") { value("Group not found: $groupId") }
            }
    }

    @Test
    fun `DELETE child group from parent group returns updated group`() {
        val parentId = UUID.randomUUID()
        val childId = UUID.randomUUID()
        val updatedGroup = aGroup(id = parentId, name = "Parent Group")
        `when`(ohGroupService.removeGroupFromGroup(parentId, childId)).thenReturn(updatedGroup)

        mockMvc.delete("/api/openinghours/group/$parentId/groups/$childId")
            .andExpect {
                status { isOk() }
                jsonPath("$.name") { value("Parent Group") }
            }
    }

    @Test
    fun `DELETE child group from parent group returns 404 when child group is not a member`() {
        val parentId = UUID.randomUUID()
        val childId = UUID.randomUUID()
        `when`(ohGroupService.removeGroupFromGroup(parentId, childId))
            .thenThrow(ResponseStatusException(HttpStatus.NOT_FOUND, "Group $childId is not a member of group $parentId"))

        mockMvc.delete("/api/openinghours/group/$parentId/groups/$childId")
            .andExpect {
                status { isNotFound() }
                jsonPath("$.message") { value("Group $childId is not a member of group $parentId") }
            }
    }

    @Test
    fun `DELETE child group from parent group returns 404 when parent group does not exist`() {
        val parentId = UUID.randomUUID()
        val childId = UUID.randomUUID()
        `when`(ohGroupService.removeGroupFromGroup(parentId, childId))
            .thenThrow(ResponseStatusException(HttpStatus.NOT_FOUND, "Group not found: $parentId"))

        mockMvc.delete("/api/openinghours/group/$parentId/groups/$childId")
            .andExpect {
                status { isNotFound() }
                jsonPath("$.message") { value("Group not found: $parentId") }
            }
    }
}
