package no.nav.openinghours.controllers

import no.nav.openinghours.model.db.Rule
import no.nav.openinghours.service.RuleService
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
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

    @MockBean
    private lateinit var ruleService: RuleService

    private fun aRule(id: UUID = UUID.randomUUID(), name: String = "Weekdays", rule: String = "??.??.???? ? 1-5 08:00-16:00") =
        Rule.create(id = id, name = name, rule = rule, header = "Header", text = "Text", onlyShowForNavEmployees = false)

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
    fun `GET rule by id returns 200 with null body when not found`() {
        val id = UUID.randomUUID()
        `when`(ruleService.get(id)).thenReturn(null)

        mockMvc.get("/api/openinghours/rule/$id")
            .andExpect { status { isOk() } }
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

        mockMvc.put("/api/openinghours/rule?name=Weekdays&rule=??.??.???? ? 1-5 08:00-16:00&header=H&text=T&onlyShowForNavEmployees=false")
            .andExpect {
                status { isOk() }
                jsonPath("$.name") { value("Weekdays") }
            }
    }

    @Test
    fun `PUT upsert with blank name returns 400`() {
        `when`(ruleService.upsert("", "??.??.???? ? 1-5 08:00-16:00", null, null, false))
            .thenThrow(ResponseStatusException(HttpStatus.BAD_REQUEST, "Name must not be blank"))

        mockMvc.put("/api/openinghours/rule?name=&rule=??.??.???? ? 1-5 08:00-16:00&header&text")
            .andExpect { status { isBadRequest() } }
    }

    @Test
    fun `PUT upsert with invalid rule format returns 400`() {
        `when`(ruleService.upsert("Test", "invalid", null, null, false))
            .thenThrow(ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid rule format"))

        mockMvc.put("/api/openinghours/rule?name=Test&rule=invalid&header&text")
            .andExpect { status { isBadRequest() } }
    }

    @Test
    fun `PATCH update partial fields`() {
        val id = UUID.randomUUID()
        val updated = aRule(id = id, name = "Updated")
        `when`(ruleService.update(id, "Updated", null, null, null, null)).thenReturn(updated)

        mockMvc.patch("/api/openinghours/rule/$id?name=Updated")
            .andExpect {
                status { isOk() }
                jsonPath("$.name") { value("Updated") }
            }
    }

    @Test
    fun `PATCH update non-existent id returns 404`() {
        val id = UUID.randomUUID()
        `when`(ruleService.update(id, "X", null, null, null, null))
            .thenThrow(ResponseStatusException(HttpStatus.NOT_FOUND, "Opening hours rule not found"))

        mockMvc.patch("/api/openinghours/rule/$id?name=X")
            .andExpect { status { isNotFound() } }
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

    @Test
    fun `GET rule with invalid UUID returns 400`() {
        mockMvc.get("/api/openinghours/rule/not-a-uuid")
            .andExpect { status { isBadRequest() } }
    }
}

