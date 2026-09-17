package no.nav.openinghours.controllers

import no.nav.openinghours.model.db.OhGroup
import no.nav.openinghours.model.db.Rule
import no.nav.openinghours.service.RuleService
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mockito
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
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

    private fun aRule(
        id: UUID = UUID.randomUUID(),
        name: String = "Weekdays",
        rule: String = "??.??.???? ? 1-5 08:00-16:00",
        unstableOpeningHours: Boolean = false
    ) =
        Rule.create(
            id = id,
            name = name,
            rule = rule,
            header = "Header",
            text = "Text",
            onlyShowForNavEmployees = false,
            redDay = false,
            unstableOpeningHours = unstableOpeningHours
        )

    @Test
    fun `GET all rules returns flagged and unflagged rules alike`() {
        val rules = listOf(
            aRule(name = "Flaky", unstableOpeningHours = true),
            aRule(name = "Steady", unstableOpeningHours = false)
        )
        `when`(ruleService.getAll()).thenReturn(rules)

        mockMvc.get("/api/openinghours/rule")
            .andExpect {
                status { isOk() }
                jsonPath("$.length()") { value(2) }
                jsonPath("$[0].unstableOpeningHours") { value(true) }
                jsonPath("$[1].unstableOpeningHours") { value(false) }
            }
    }

    @Test
    fun `PUT upsert forwards unstableOpeningHours true`() {
        `when`(
            ruleService.upsert(
                "Weekdays", "??.??.???? ? 1-5 08:00-16:00", "H", "T",
                onlyShowForNavEmployees = false, unstableOpeningHours = true
            )
        ).thenReturn(aRule(unstableOpeningHours = true))

        mockMvc.put("/api/openinghours/rule") {
            param("name", "Weekdays")
            param("rule", "??.??.???? ? 1-5 08:00-16:00")
            param("header", "H")
            param("text", "T")
            param("onlyShowForNavEmployees", "false")
            param("unstableOpeningHours", "true")
        }.andExpect {
            status { isOk() }
            jsonPath("$.unstableOpeningHours") { value(true) }
        }
    }

    @Test
    fun `PUT upsert defaults unstableOpeningHours to false when the param is absent`() {
        `when`(
            ruleService.upsert(
                "Weekdays", "??.??.???? ? 1-5 08:00-16:00", null, null,
                onlyShowForNavEmployees = false, unstableOpeningHours = false
            )
        ).thenReturn(aRule())

        mockMvc.put("/api/openinghours/rule") {
            param("name", "Weekdays")
            param("rule", "??.??.???? ? 1-5 08:00-16:00")
        }.andExpect {
            status { isOk() }
            jsonPath("$.unstableOpeningHours") { value(false) }
        }
    }

    @Test
    fun `PATCH forwards unstableOpeningHours to the service`() {
        val id = UUID.randomUUID()
        `when`(ruleService.update(id, null, null, null, null, null, true))
            .thenReturn(aRule(id = id, unstableOpeningHours = true))

        mockMvc.patch("/api/openinghours/rule/$id") {
            param("unstableOpeningHours", "true")
        }.andExpect {
            status { isOk() }
            jsonPath("$.unstableOpeningHours") { value(true) }
        }
    }

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

    // --- /outdated: year selection ------------------------------------------------------------

    @Test
    fun `GET outdated returns the rules for the selected year`() {
        `when`(ruleService.findByYear(2024)).thenReturn(
            listOf(
                aRule(name = "juledag 2024", rule = "25.12.2024 ? ? 00:00-00:00"),
                aRule(name = "nyttår 2024", rule = "31.12.2024 ? ? 09:00-14:00")
            )
        )

        mockMvc.get("/api/openinghours/rule/outdated") {
            param("year", "2024")
        }.andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(2) }
            jsonPath("$[0].name") { value("juledag 2024") }
            jsonPath("$[1].name") { value("nyttår 2024") }
        }

        verify(ruleService).findByYear(2024)
    }

    @Test
    fun `GET outdated returns 200 with empty list when the year holds no rules`() {
        `when`(ruleService.findByYear(2019)).thenReturn(emptyList())

        mockMvc.get("/api/openinghours/rule/outdated") {
            param("year", "2019")
        }.andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(0) }
        }
    }

    @Test
    fun `GET outdated without a year returns 400`() {
        mockMvc.get("/api/openinghours/rule/outdated")
            .andExpect {
                status { isBadRequest() }
                jsonPath("$.message") { value("You must specify the year") }
            }

        verify(ruleService, never()).findByYear(anyInt())
    }

    @Test
    fun `GET outdated with a blank year returns the same explicit 400`() {
        mockMvc.get("/api/openinghours/rule/outdated") {
            param("year", "")
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.message") { value("You must specify the year") }
        }

        verify(ruleService, never()).findByYear(anyInt())
    }

    @Test
    fun `GET outdated with a non-numeric year returns 400`() {
        mockMvc.get("/api/openinghours/rule/outdated") {
            param("year", "toothousand")
        }.andExpect { status { isBadRequest() } }

        verify(ruleService, never()).findByYear(anyInt())
    }

    @Test
    fun `GET outdated propagates the current-year rejection from the service`() {
        `when`(ruleService.findByYear(2026)).thenThrow(
            ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Rules from the current year (2026) cannot be deleted as outdated"
            )
        )

        mockMvc.get("/api/openinghours/rule/outdated") {
            param("year", "2026")
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.message") { value("Rules from the current year (2026) cannot be deleted as outdated") }
        }
    }

    // --- /outdated: delete confirmation branching ----------------------------------------------

    @Test
    fun `DELETE outdated without confirm returns 409 previewing the rules and deletes nothing`() {
        `when`(ruleService.findByYear(2024)).thenReturn(
            listOf(
                aRule(name = "juledag 2024", rule = "25.12.2024 ? ? 00:00-00:00"),
                aRule(name = "nyttår 2024", rule = "31.12.2024 ? ? 09:00-14:00")
            )
        )

        mockMvc.delete("/api/openinghours/rule/outdated") {
            param("year", "2024")
        }.andExpect {
            status { isConflict() }
            jsonPath("$.message") {
                value("2 rule(s) from 2024 would be deleted: juledag 2024, nyttår 2024. Pass ?confirm=true to proceed.")
            }
        }

        verify(ruleService, never()).deleteByYear(anyInt())
    }

    @Test
    fun `DELETE outdated with confirm=false is treated the same as omitting it`() {
        `when`(ruleService.findByYear(2024))
            .thenReturn(listOf(aRule(name = "juledag 2024", rule = "25.12.2024 ? ? 00:00-00:00")))

        mockMvc.delete("/api/openinghours/rule/outdated") {
            param("year", "2024")
            param("confirm", "false")
        }.andExpect {
            status { isConflict() }
            jsonPath("$.message") {
                value("1 rule(s) from 2024 would be deleted: juledag 2024. Pass ?confirm=true to proceed.")
            }
        }

        verify(ruleService, never()).deleteByYear(anyInt())
    }

    @Test
    fun `DELETE outdated without confirm returns 200 and empty list when there is nothing to delete`() {
        `when`(ruleService.findByYear(2019)).thenReturn(emptyList())

        mockMvc.delete("/api/openinghours/rule/outdated") {
            param("year", "2019")
        }.andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(0) }
        }

        verify(ruleService, never()).deleteByYear(anyInt())
    }

    @Test
    fun `DELETE outdated with confirm=true deletes and returns the removed rules`() {
        `when`(ruleService.deleteByYear(2024))
            .thenReturn(listOf(aRule(name = "juledag 2024", rule = "25.12.2024 ? ? 00:00-00:00")))

        mockMvc.delete("/api/openinghours/rule/outdated") {
            param("year", "2024")
            param("confirm", "true")
        }.andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(1) }
            jsonPath("$[0].name") { value("juledag 2024") }
        }

        verify(ruleService).deleteByYear(2024)
        // confirm=true skips the preview entirely
        verify(ruleService, never()).findByYear(anyInt())
    }

    @Test
    fun `DELETE outdated without a year returns 400 and touches nothing`() {
        mockMvc.delete("/api/openinghours/rule/outdated")
            .andExpect {
                status { isBadRequest() }
                jsonPath("$.message") { value("You must specify the year") }
            }

        verify(ruleService, never()).findByYear(anyInt())
        verify(ruleService, never()).deleteByYear(anyInt())
    }

    @Test
    fun `DELETE outdated without a year is rejected even when confirm is set`() {
        mockMvc.delete("/api/openinghours/rule/outdated") {
            param("confirm", "true")
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.message") { value("You must specify the year") }
        }

        verify(ruleService, never()).deleteByYear(anyInt())
    }

    @Test
    fun `DELETE outdated with a non-numeric year returns 400 and touches nothing`() {
        mockMvc.delete("/api/openinghours/rule/outdated") {
            param("year", "2o24")
        }.andExpect { status { isBadRequest() } }

        verify(ruleService, never()).findByYear(anyInt())
        verify(ruleService, never()).deleteByYear(anyInt())
    }

    @Test
    fun `DELETE outdated with confirm=true still cannot delete the current year`() {
        `when`(ruleService.deleteByYear(2026)).thenThrow(
            ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Rules from the current year (2026) cannot be deleted as outdated"
            )
        )

        mockMvc.delete("/api/openinghours/rule/outdated") {
            param("year", "2026")
            param("confirm", "true")
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.message") { value("Rules from the current year (2026) cannot be deleted as outdated") }
        }
    }
}

