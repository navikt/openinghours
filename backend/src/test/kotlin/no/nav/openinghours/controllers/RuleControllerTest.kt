package no.nav.openinghours.controllers

import no.nav.openinghours.model.db.OhGroup
import no.nav.openinghours.model.db.Rule
import no.nav.openinghours.service.RuleService
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.http.HttpStatus
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.put
import org.springframework.test.web.servlet.patch
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

@WebMvcTest(RuleController::class)
@ActiveProfiles("mock")
@AutoConfigureMockMvc(addFilters = false)
class RuleControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var ruleService: RuleService

    @Suppress("UNCHECKED_CAST")
    private fun <T> anyArg(): T = Mockito.any<T>() as T

    private fun aRule(id: UUID = UUID.randomUUID(), name: String = "Weekdays", rule: String = "??.??.???? ? 1-5 08:00-16:00") =
        Rule.create(id = id, name = name, rule = rule, header = "Header", text = "Text", onlyShowForNavEmployees = false, redDay = false)

    @Test
    fun `GET rule by id returns rule`() {
        val id = UUID.randomUUID()
        val rule = aRule(id = id)
        `when`(ruleService.get(id)).thenReturn(rule)

        mockMvc.get("/api/openinghours/rule/$id")
            .andExpect {
                status { isOk() }
                jsonPath("$.name") { value("Weekdays") }
            }
    }

    @Test
    fun `GET rule by id returns 404 when not found`() {
        val id = UUID.randomUUID()
        `when`(ruleService.get(id)).thenThrow(ResponseStatusException(HttpStatus.NOT_FOUND, "Rule not found: $id"))

        mockMvc.get("/api/openinghours/rule/$id")
            .andExpect {
                status { isNotFound() }
                jsonPath("$.message") { value("Rule not found: $id") }
            }
    }

    @Test
    fun `GET all rules returns list`() {
        val rules = listOf(aRule(name = "R1"), aRule(name = "R2"))
        `when`(ruleService.getAll()).thenReturn(rules)

        mockMvc.get("/api/openinghours/rule")
            .andExpect {
                status { isOk() }
                jsonPath("$.length()") { value(2) }
                jsonPath("$[0].name") { value("R1") }
                jsonPath("$[1].name") { value("R2") }
            }
    }

    @Test
    fun `PUT upsert creates new rule`() {
        val rule = aRule()
        `when`(ruleService.upsert("Weekdays", "??.??.???? ? 1-5 08:00-16:00", "H", "T", false)).thenReturn(rule)

        mockMvc.put("/api/openinghours/rule") {
            param("name", "Weekdays")
            param("rule", "??.??.???? ? 1-5 08:00-16:00")
            param("header", "H")
            param("text", "T")
            param("onlyShowForNavEmployees", "false")
        }.andExpect {
            status { isOk() }
            jsonPath("$.name") { value("Weekdays") }
        }
    }

    @Test
    fun `PUT upsert with blank name returns 400`() {
        `when`(ruleService.upsert("", "??.??.???? ? 1-5 08:00-16:00", null, null, false))
            .thenThrow(ResponseStatusException(HttpStatus.BAD_REQUEST, "Name must not be blank"))

        mockMvc.put("/api/openinghours/rule") {
            param("name", "")
            param("rule", "??.??.???? ? 1-5 08:00-16:00")
        }.andExpect { status { isBadRequest() } }
    }

    @Test
    fun `PUT upsert with invalid rule format returns 400`() {
        `when`(ruleService.upsert("Test", "invalid", null, null, false))
            .thenThrow(ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid rule format"))

        mockMvc.put("/api/openinghours/rule") {
            param("name", "Test")
            param("rule", "invalid")
        }.andExpect { status { isBadRequest() } }
    }

    @Test
    fun `PATCH update partial fields`() {
        val id = UUID.randomUUID()
        val updated = aRule(id = id, name = "Updated")
        `when`(ruleService.update(id, "Updated", null, null, null, null)).thenReturn(updated)

        mockMvc.patch("/api/openinghours/rule/$id") {
            param("name", "Updated")
        }.andExpect {
            status { isOk() }
            jsonPath("$.name") { value("Updated") }
        }
    }

    @Test
    fun `PATCH update non-existent id returns 404`() {
        val id = UUID.randomUUID()
        `when`(ruleService.update(id, "X", null, null, null, null))
            .thenThrow(ResponseStatusException(HttpStatus.NOT_FOUND, "Rule not found: $id"))

        mockMvc.patch("/api/openinghours/rule/$id") {
            param("name", "X")
        }.andExpect { status { isNotFound() } }
    }

    @Test
    fun `DELETE rule returns true`() {
        val id = UUID.randomUUID()
        `when`(ruleService.delete(id)).thenReturn(true)

        mockMvc.delete("/api/openinghours/rule/$id")
            .andExpect {
                status { isOk() }
                jsonPath("$") { value(true) }
            }
    }

    @Test
    fun `DELETE rule returns false for unknown id`() {
        val id = UUID.randomUUID()
        `when`(ruleService.delete(id)).thenReturn(false)

        mockMvc.delete("/api/openinghours/rule/$id")
            .andExpect {
                status { isOk() }
                jsonPath("$") { value(false) }
            }
    }

    @org.junit.jupiter.api.BeforeEach
    fun defaultStubs() {
        `when`(ruleService.getGroupsByRuleId(anyArg())).thenReturn(emptyList())
    }

    @Test
    fun `DELETE rule returns 409 with group names when rule is used by groups and confirm is not set`() {
        val id = UUID.randomUUID()
        val groups = listOf(
            OhGroup.create(name = "Group A", ruleGroupIds = listOf(id)),
            OhGroup.create(name = "Group B", ruleGroupIds = listOf(id))
        )
        `when`(ruleService.getGroupsByRuleId(id)).thenReturn(groups)

        mockMvc.delete("/api/openinghours/rule/$id")
            .andExpect {
                status { isConflict() }
                jsonPath("$.message") { value("Rule is used by 2 group(s): Group A, Group B. Pass ?confirm=true to delete anyway.") }
            }
    }

    @Test
    fun `DELETE rule with confirm=true deletes even when rule is used by groups`() {
        val id = UUID.randomUUID()
        `when`(ruleService.delete(id)).thenReturn(true)

        mockMvc.delete("/api/openinghours/rule/$id") {
            param("confirm", "true")
        }.andExpect {
            status { isOk() }
            jsonPath("$") { value(true) }
        }
    }

    @Test
    fun `GET rule with invalid UUID returns 400`() {
        mockMvc.get("/api/openinghours/rule/not-a-uuid")
            .andExpect { status { isBadRequest() } }
    }

    @Test
    fun `GET groups for rule returns list of groups`() {
        val ruleId = UUID.randomUUID()
        val groups = listOf(
            OhGroup.create(name = "Group A", ruleGroupIds = listOf(ruleId)),
            OhGroup.create(name = "Group B", ruleGroupIds = listOf(ruleId))
        )
        `when`(ruleService.getGroupsByRuleId(ruleId)).thenReturn(groups)

        mockMvc.get("/api/openinghours/rule/$ruleId/groups")
            .andExpect {
                status { isOk() }
                jsonPath("$.length()") { value(2) }
                jsonPath("$[0].name") { value("Group A") }
                jsonPath("$[1].name") { value("Group B") }
            }
    }

    @Test
    fun `GET groups for rule returns 404 when rule does not exist`() {
        val ruleId = UUID.randomUUID()
        `when`(ruleService.getGroupsByRuleId(ruleId))
            .thenThrow(ResponseStatusException(HttpStatus.NOT_FOUND, "Rule not found: $ruleId"))

        mockMvc.get("/api/openinghours/rule/$ruleId/groups")
            .andExpect {
                status { isNotFound() }
                jsonPath("$.message") { value("Rule not found: $ruleId") }
            }
    }

    @Test
    fun `GET groups for rule returns 200 with empty list when rule exists but has no groups`() {
        val ruleId = UUID.randomUUID()
        `when`(ruleService.getGroupsByRuleId(ruleId)).thenReturn(emptyList())

        mockMvc.get("/api/openinghours/rule/$ruleId/groups")
            .andExpect {
                status { isOk() }
                jsonPath("$.length()") { value(0) }
            }
    }
}

