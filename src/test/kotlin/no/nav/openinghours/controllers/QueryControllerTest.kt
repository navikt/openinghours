package no.nav.openinghours.controllers

import no.nav.openinghours.evaluator.NorwegianPublicHolidays
import no.nav.openinghours.evaluator.OpeningHoursDisplayData
import no.nav.openinghours.evaluator.OpeningHoursEvaluator
import no.nav.openinghours.model.db.Service
import no.nav.openinghours.model.db.ServiceType
import no.nav.openinghours.service.DisplayDataResult
import no.nav.openinghours.service.OpeningHoursLookupService
import no.nav.openinghours.service.ServiceService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.web.server.ResponseStatusException
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

@WebMvcTest(QueryController::class)
@Import(NorwegianPublicHolidays::class)
@ActiveProfiles("mock")
@AutoConfigureMockMvc(addFilters = false)
class QueryControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var lookupService: OpeningHoursLookupService

    @MockitoBean
    private lateinit var serviceService: ServiceService

    @MockitoBean
    private lateinit var clock: Clock

    @BeforeEach
    fun setupClock() {
        // Fix the clock at 10:00 UTC so time-dependent isOpen assertions are deterministic
        val fixed = Clock.fixed(Instant.parse("2024-03-15T10:00:00Z"), ZoneOffset.UTC)
        `when`(clock.instant()).thenReturn(fixed.instant())
        `when`(clock.zone).thenReturn(fixed.zone)
    }


    @Test
    fun `query range includes warningMessage and null matchedRule for dates with no matching rule`() {
        // 2024-03-15 has a matching rule; 2024-03-16 has no matching rule (fallback).
        val serviceId = UUID.randomUUID()
        val groupId = UUID.randomUUID()
        val matchDate = LocalDate.of(2024, 3, 15)
        val noMatchDate = LocalDate.of(2024, 3, 16)
        val expectedWarning = "Group: $groupId has rules defined, but none match the requested date: $noMatchDate. " +
            "Default rule ID will be used because no valid rule is assigned."

        `when`(serviceService.getOhGroupIdsForService(serviceId)).thenReturn(listOf(groupId))
        `when`(serviceService.get(serviceId)).thenReturn(Service.create(name = "Bidrag", type = ServiceType.TJENESTE, team = "team"))
        `when`(lookupService.getDisplayDataOrDefault(groupId, matchDate)).thenReturn(
            DisplayDataResult(OpeningHoursDisplayData(openingHours = "07:00-21:00", ruleName = "Weekdays", rule = "??.??.???? ? 1-5 07:00-21:00"))
        )
        `when`(lookupService.getDisplayDataOrDefault(groupId, noMatchDate)).thenReturn(
            DisplayDataResult(
                data = OpeningHoursEvaluator.DEFAULT_DISPLAY_DATA,
                warningMessage = expectedWarning,
            )
        )

        mockMvc.get("/api/openinghours/query/service/$serviceId/range?from=2024-03-15&to=2024-03-16")
            .andExpect {
                status { isOk() }
                jsonPath("$.length()") { value(2) }
                jsonPath("$[0].serviceName") { value("Bidrag") }
                // matched day — no warning, matchedRule present
                jsonPath("$[0].warningMessage") { doesNotExist() }
                jsonPath("$[0].matchedRule.name") { value("Weekdays") }
                jsonPath("$[1].serviceName") { value("Bidrag") }
                // no-match day — warning present, matchedRule absent
                jsonPath("$[1].warningMessage") { value(expectedWarning) }
                jsonPath("$[1].matchedRule") { doesNotExist() }
                // default data: open all day
                jsonPath("$[1].openingTime") { value("00:00") }
                jsonPath("$[1].closingTime") { value("23:59") }
            }
    }

    @Test
    fun `query range returns full range even when every date has no matching rule`() {
        // All dates fall back to DEFAULT_DISPLAY_DATA with a warning — response must still be 200 with all entries.
        val serviceId = UUID.randomUUID()
        val groupId = UUID.randomUUID()

        `when`(serviceService.getOhGroupIdsForService(serviceId)).thenReturn(listOf(groupId))
        `when`(serviceService.get(serviceId)).thenReturn(Service.create(name = "Bidrag", type = ServiceType.TJENESTE, team = "team"))
        listOf(LocalDate.of(2024, 3, 15), LocalDate.of(2024, 3, 16)).forEach { d ->
            `when`(lookupService.getDisplayDataOrDefault(groupId, d)).thenReturn(
                DisplayDataResult(
                    data = OpeningHoursEvaluator.DEFAULT_DISPLAY_DATA,
                    warningMessage = "Group: $groupId has rules defined, but none match the requested date: $d. " +
                        "Default rule ID will be used because no valid rule is assigned.",
                )
            )
        }

        mockMvc.get("/api/openinghours/query/service/$serviceId/range?from=2024-03-15&to=2024-03-16")
            .andExpect {
                status { isOk() }
                jsonPath("$.length()") { value(2) }
                jsonPath("$[0].warningMessage") { isString() }
                jsonPath("$[1].warningMessage") { isString() }
            }
    }

    @Test
    fun `query by service returns opening hours`() {
        val serviceId = UUID.randomUUID()
        val groupId = UUID.randomUUID()
        val date = LocalDate.of(2024, 3, 15)

        `when`(serviceService.getOhGroupIdsForService(serviceId)).thenReturn(listOf(groupId))
        `when`(serviceService.get(serviceId)).thenReturn(Service.create(name = "Bidrag", type = ServiceType.TJENESTE, team = "team"))
        `when`(lookupService.getDisplayData(groupId, date)).thenReturn(
            OpeningHoursDisplayData(openingHours = "07:00-21:00", ruleName = "Standard weekdays", rule = "??.??.???? ? 1-5 07:00-21:00")
        )

        mockMvc.get("/api/openinghours/query/service/$serviceId?date=2024-03-15")
            .andExpect {
                status { isOk() }
                jsonPath("$.serviceName") { value("Bidrag") }
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
            OpeningHoursDisplayData(openingHours = "00:00-00:00", ruleName = "weekend-closed", rule = "??.??.???? ? 6-7 00:00-00:00")
        )

        mockMvc.get("/api/openinghours/query/group/$groupId?date=2023-07-22")
            .andExpect {
                status { isOk() }
                jsonPath("$.isOpen") { value(false) }
                jsonPath("$.matchedRule.name") { value("weekend-closed") }
                jsonPath("$.matchedRule.rule") { value("??.??.???? ? 6-7 00:00-00:00") }
            }
    }

    @Test
    fun `query range returns multiple days`() {
        val serviceId = UUID.randomUUID()
        val groupId = UUID.randomUUID()

        `when`(serviceService.getOhGroupIdsForService(serviceId)).thenReturn(listOf(groupId))
        `when`(serviceService.get(serviceId)).thenReturn(Service.create(name = "Bidrag", type = ServiceType.TJENESTE, team = "team"))
        `when`(lookupService.getDisplayDataOrDefault(groupId, LocalDate.of(2024, 3, 15))).thenReturn(
            DisplayDataResult(OpeningHoursDisplayData(openingHours = "07:00-21:00", ruleName = "Weekdays", rule = "??.??.???? ? 1-5 07:00-21:00"))
        )
        `when`(lookupService.getDisplayDataOrDefault(groupId, LocalDate.of(2024, 3, 16))).thenReturn(
            DisplayDataResult(OpeningHoursDisplayData(openingHours = "00:00-00:00", ruleName = "weekend-closed", rule = "??.??.???? ? 6-7 00:00-00:00"))
        )

        mockMvc.get("/api/openinghours/query/service/$serviceId/range?from=2024-03-15&to=2024-03-16")
            .andExpect {
                status { isOk() }
                jsonPath("$.length()") { value(2) }
                jsonPath("$[0].isOpen") { value(true) }
                jsonPath("$[1].isOpen") { value(false) }
            }
    }

    @Test
    fun `query by service returns displayHeader and onlyShowForNavEmployees`() {
        val serviceId = UUID.randomUUID()
        val groupId = UUID.randomUUID()
        val date = LocalDate.of(2024, 3, 15)

        `when`(serviceService.getOhGroupIdsForService(serviceId)).thenReturn(listOf(groupId))
        `when`(serviceService.get(serviceId)).thenReturn(Service.create(name = "Bidrag", type = ServiceType.TJENESTE, team = "team"))
        `when`(lookupService.getDisplayData(groupId, date)).thenReturn(
            OpeningHoursDisplayData(
                openingHours = "09:00-15:00",
                ruleName = "Internal",
                rule = "??.??.???? ? 1-5 09:00-15:00",
                displayHeader = "Intern åpningstid",
                displayText = "Kun for ansatte",
                onlyShowForNavEmployees = true,
                redDay = false,
            )
        )

        mockMvc.get("/api/openinghours/query/service/$serviceId?date=2024-03-15")
            .andExpect {
                status { isOk() }
                jsonPath("$.serviceName") { value("Bidrag") }
                jsonPath("$.isOpen") { value(true) }
                jsonPath("$.openingTime") { value("09:00") }
                jsonPath("$.closingTime") { value("15:00") }
                jsonPath("$.displayHeader") { value("Intern åpningstid") }
                jsonPath("$.displayText") { value("Kun for ansatte") }
                jsonPath("$.onlyShowForNavEmployees") { value(true) }
                jsonPath("$.redDay") { value(false) }
                jsonPath("$.matchedRule.name") { value("Internal") }
            }
    }

    @Test
    fun `query by group returns redDay true for a public holiday rule`() {
        val groupId = UUID.randomUUID()
        val date = LocalDate.of(2024, 12, 25)

        `when`(lookupService.getDisplayData(groupId, date)).thenReturn(
            OpeningHoursDisplayData(
                openingHours = "00:00-00:00",
                ruleName = "Christmas",
                rule = "25.12.???? ? ? 00:00-00:00",
                redDay = true,
            )
        )

        mockMvc.get("/api/openinghours/query/group/$groupId?date=2024-12-25")
            .andExpect {
                status { isOk() }
                jsonPath("$.isOpen") { value(false) }
                jsonPath("$.redDay") { value(true) }
                jsonPath("$.matchedRule.name") { value("Christmas") }
            }
    }

    @Test
    fun `query by group returns 404 when rules exist but none match the date`() {
        val groupId = UUID.randomUUID()
        val date = LocalDate.of(2024, 3, 16)

        `when`(lookupService.getDisplayData(groupId, date)).thenThrow(
            ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Group '$groupId' has rules defined, but none match the requested date: $date"
            )
        )

        mockMvc.get("/api/openinghours/query/group/$groupId?date=2024-03-16")
            .andExpect { status { isNotFound() } }
    }

    @Test
    fun `query by group returns open all day when group has no rules at all`() {
        val groupId = UUID.randomUUID()
        val date = LocalDate.of(2024, 3, 16)

        `when`(lookupService.getDisplayData(groupId, date)).thenReturn(
            OpeningHoursDisplayData(
                ruleName = "No Rules stated",
                rule = "??.??.???? ? ? 00:00-23:59",
                openingHours = "00:00-23:59",
                displayHeader = "Default regel",
                displayText = "Åpent - ingen gjeldende dato regler",
                onlyShowForNavEmployees = false
            )
        )

        mockMvc.get("/api/openinghours/query/group/$groupId?date=2024-03-16")
            .andExpect {
                status { isOk() }
                jsonPath("$.isOpen") { value(true) }
                jsonPath("$.openingTime") { value("00:00") }
                jsonPath("$.closingTime") { value("23:59") }
                jsonPath("$.displayHeader") { value("Default regel") }
                jsonPath("$.displayText") { value("Åpent - ingen gjeldende dato regler") }
                jsonPath("$.onlyShowForNavEmployees") { value(false) }
                jsonPath("$.matchedRule.name") { value("No Rules stated") }
                jsonPath("$.matchedRule.rule") { value("??.??.???? ? ? 00:00-23:59") }
            }
    }

    @Test
    fun `query range returns 404 when no group assigned`() {
        val serviceId = UUID.randomUUID()
        `when`(serviceService.getOhGroupIdsForService(serviceId)).thenReturn(emptyList())

        mockMvc.get("/api/openinghours/query/service/$serviceId/range?from=2024-03-15&to=2024-03-16")
            .andExpect { status { isNotFound() } }
    }

    // ── isOpen semantics: today vs non-today ──────────────────────────────

    @Test
    fun `isOpen uses real-time check for today - before opening returns false`() {
        // Clock is at 10:00; hours 11:00-17:00 → not yet open
        val groupId = UUID.randomUUID()
        val today = LocalDate.of(2024, 3, 15)

        `when`(lookupService.getDisplayData(groupId, today)).thenReturn(
            OpeningHoursDisplayData(openingHours = "11:00-17:00", ruleName = "Late open", rule = "??.??.???? ? ? 11:00-17:00")
        )

        mockMvc.get("/api/openinghours/query/group/$groupId?date=2024-03-15")
            .andExpect {
                status { isOk() }
                jsonPath("$.isOpen") { value(false) }
            }
    }

    @Test
    fun `isOpen uses open-at-all semantics for non-today date - partial hours yield true`() {
        // 2024-03-16 is not today (clock = 2024-03-15); hours 11:00-17:00 → open at some point
        val groupId = UUID.randomUUID()
        val future = LocalDate.of(2024, 3, 16)

        `when`(lookupService.getDisplayData(groupId, future)).thenReturn(
            OpeningHoursDisplayData(openingHours = "11:00-17:00", ruleName = "Standard", rule = "??.??.???? ? 1-5 11:00-17:00")
        )

        mockMvc.get("/api/openinghours/query/group/$groupId?date=2024-03-16")
            .andExpect {
                status { isOk() }
                jsonPath("$.isOpen") { value(true) }
            }
    }

    @Test
    fun `isOpen uses open-at-all semantics for non-today date - closed all day yields false`() {
        // 2024-03-16 is not today; hours 00:00-00:00 → closed all day
        val groupId = UUID.randomUUID()
        val future = LocalDate.of(2024, 3, 16)

        `when`(lookupService.getDisplayData(groupId, future)).thenReturn(
            OpeningHoursDisplayData(openingHours = "00:00-00:00", ruleName = "Closed", rule = "??.??.???? ? 6-7 00:00-00:00")
        )

        mockMvc.get("/api/openinghours/query/group/$groupId?date=2024-03-16")
            .andExpect {
                status { isOk() }
                jsonPath("$.isOpen") { value(false) }
            }
    }

    @Test
    fun `query range isOpen reflects open-at-all for non-today entries`() {
        // Clock = 2024-03-15T10:00. Range 2024-03-14 (past) to 2024-03-16 (future).
        // Past/future entries use open-at-all semantics; today uses real-time.
        val serviceId = UUID.randomUUID()
        val groupId = UUID.randomUUID()

        `when`(serviceService.getOhGroupIdsForService(serviceId)).thenReturn(listOf(groupId))
        `when`(serviceService.get(serviceId)).thenReturn(Service.create(name = "Bidrag", type = ServiceType.TJENESTE, team = "team"))
        `when`(lookupService.getDisplayDataOrDefault(groupId, LocalDate.of(2024, 3, 14))).thenReturn(
            DisplayDataResult(OpeningHoursDisplayData(openingHours = "07:00-21:00", ruleName = "Weekday", rule = "??.??.???? ? 1-5 07:00-21:00"))
        )
        `when`(lookupService.getDisplayDataOrDefault(groupId, LocalDate.of(2024, 3, 15))).thenReturn(
            DisplayDataResult(OpeningHoursDisplayData(openingHours = "07:00-21:00", ruleName = "Weekday", rule = "??.??.???? ? 1-5 07:00-21:00"))
        )
        `when`(lookupService.getDisplayDataOrDefault(groupId, LocalDate.of(2024, 3, 16))).thenReturn(
            DisplayDataResult(OpeningHoursDisplayData(openingHours = "00:00-00:00", ruleName = "Weekend", rule = "??.??.???? ? 6-7 00:00-00:00"))
        )

        mockMvc.get("/api/openinghours/query/service/$serviceId/range?from=2024-03-14&to=2024-03-16")
            .andExpect {
                status { isOk() }
                jsonPath("$.length()") { value(3) }
                // 2024-03-14 (past, not today): open at all → true
                jsonPath("$[0].isOpen") { value(true) }
                // 2024-03-15 (today, clock=10:00, hours 07:00-21:00): open right now → true
                jsonPath("$[1].isOpen") { value(true) }
                // 2024-03-16 (future, not today): 00:00-00:00 → false
                jsonPath("$[2].isOpen") { value(false) }
            }
    }

    // ── Malformed hours: fallback consistency ─────────────────────────────

    @Test
    fun `malformed hours on today yields isOpen=false and closed sentinel times`() {
        // Clock is at 2024-03-15T10:00. A stored hours string that cannot be parsed
        // must not produce the all-day window (00:00–23:59) alongside isOpen=false.
        val groupId = UUID.randomUUID()
        val today = LocalDate.of(2024, 3, 15)

        `when`(lookupService.getDisplayData(groupId, today)).thenReturn(
            OpeningHoursDisplayData(openingHours = "BADFORMAT", ruleName = "Broken", rule = "??.??.???? ? ? BADFORMAT")
        )

        mockMvc.get("/api/openinghours/query/group/$groupId?date=2024-03-15")
            .andExpect {
                status { isOk() }
                // computeIsOpenOnDate: today + malformed → false
                jsonPath("$.isOpen") { value(false) }
                // Times must not suggest "open all day" — use closed sentinel
                jsonPath("$.openingTime") { value("00:00") }
                jsonPath("$.closingTime") { value("00:00") }
            }
    }

    @Test
    fun `malformed hours on non-today yields isOpen=true and open-all-day sentinel times`() {
        // 2024-03-16 is not today (clock = 2024-03-15). A stored hours string that
        // cannot be parsed for a non-today date: isOpen=true (not the always-closed
        // sentinel), so the displayed times should be the open-all-day sentinel.
        val groupId = UUID.randomUUID()
        val future = LocalDate.of(2024, 3, 16)

        `when`(lookupService.getDisplayData(groupId, future)).thenReturn(
            OpeningHoursDisplayData(openingHours = "BADFORMAT", ruleName = "Broken", rule = "??.??.???? ? ? BADFORMAT")
        )

        mockMvc.get("/api/openinghours/query/group/$groupId?date=2024-03-16")
            .andExpect {
                status { isOk() }
                // computeIsOpenOnDate: non-today + not "00:00-00:00" → true
                jsonPath("$.isOpen") { value(true) }
                // Times consistent with open-all-day
                jsonPath("$.openingTime") { value("00:00") }
                jsonPath("$.closingTime") { value("23:59") }
            }
    }

    @Test
    fun `query range with from after to returns error`() {
        val serviceId = UUID.randomUUID()
        val groupId = UUID.randomUUID()

        `when`(serviceService.getOhGroupIdsForService(serviceId)).thenReturn(listOf(groupId))

        mockMvc.get("/api/openinghours/query/service/$serviceId/range?from=2024-03-20&to=2024-03-15")
            .andExpect {
                status { isBadRequest() }
            }
    }

    @Test
    fun `query missing date parameter returns 400`() {
        val serviceId = UUID.randomUUID()

        mockMvc.get("/api/openinghours/query/service/$serviceId")
            .andExpect { status { isBadRequest() } }
    }

    @Test
    fun `query range missing from parameter returns 400`() {
        val serviceId = UUID.randomUUID()

        mockMvc.get("/api/openinghours/query/service/$serviceId/range?to=2024-03-16")
            .andExpect { status { isBadRequest() } }
    }

    @Test
    fun `query with invalid UUID returns 400`() {
        mockMvc.get("/api/openinghours/query/service/not-a-uuid?date=2024-03-15")
            .andExpect { status { isBadRequest() } }
    }

    // ── Range: single clock snapshot across all entries ───────────────────

    @Test
    fun `query range uses a single clock snapshot - isOpen is consistent even when clock ticks during iteration`() {
        // Simulate the clock ticking across midnight to verify the range uses a single clock snapshot.
        val justBeforeMidnight = Clock.fixed(Instant.parse("2024-03-15T23:59:59Z"), ZoneOffset.UTC)
        val justAfterMidnight  = Clock.fixed(Instant.parse("2024-03-16T00:00:01Z"), ZoneOffset.UTC)
        `when`(clock.instant())
            .thenReturn(justBeforeMidnight.instant())  // first call: range snapshot
            .thenReturn(justAfterMidnight.instant())   // subsequent calls must not be used
        `when`(clock.zone).thenReturn(ZoneOffset.UTC)

        val serviceId = UUID.randomUUID()
        val groupId   = UUID.randomUUID()
        `when`(serviceService.getOhGroupIdsForService(serviceId)).thenReturn(listOf(groupId))
        `when`(serviceService.get(serviceId)).thenReturn(Service.create(name = "Bidrag", type = ServiceType.TJENESTE, team = "team"))
        // Both dates have open hours; what matters is which "today" semantics are applied.
        `when`(lookupService.getDisplayDataOrDefault(groupId, LocalDate.of(2024, 3, 15))).thenReturn(
            DisplayDataResult(OpeningHoursDisplayData(openingHours = "08:00-21:00", ruleName = "Weekday", rule = "??.??.???? ? 1-5 08:00-21:00"))
        )
        `when`(lookupService.getDisplayDataOrDefault(groupId, LocalDate.of(2024, 3, 16))).thenReturn(
            DisplayDataResult(OpeningHoursDisplayData(openingHours = "08:00-21:00", ruleName = "Weekend", rule = "??.??.???? ? 6-7 08:00-21:00"))
        )

        // At 23:59:59 with snapshot today=2024-03-15:
        //   entry 0 (2024-03-15 = today) → real-time check at 23:59:59 → open (within 08:00-21:00? No — 23:59 is after 21:00 → false)
        //   entry 1 (2024-03-16 ≠ today) → open-at-all → 08:00-21:00 ≠ 00:00-00:00 → true
        mockMvc.get("/api/openinghours/query/service/$serviceId/range?from=2024-03-15&to=2024-03-16")
            .andExpect {
                status { isOk() }
                jsonPath("$.length()") { value(2) }
                // today (2024-03-15) at 23:59:59 is after 21:00 → closed
                jsonPath("$[0].isOpen") { value(false) }
                // non-today (2024-03-16) with non-closed hours → open-at-all → true
                jsonPath("$[1].isOpen") { value(true) }
            }
    }

    @Test
    fun `query range snapshot just after midnight - yesterday in the range is treated as not-today`() {
        // Mirror of the before-midnight test: the clock has just ticked past midnight, so
        // "today" from the snapshot is 2024-03-16. A stale second read would give 2024-03-15.
        // Without the single-snapshot guarantee the two entries would swap semantics.
        val justAfterMidnight  = Clock.fixed(Instant.parse("2024-03-16T00:00:01Z"), ZoneOffset.UTC)
        val justBeforeMidnight = Clock.fixed(Instant.parse("2024-03-15T23:59:59Z"), ZoneOffset.UTC)
        `when`(clock.instant())
            .thenReturn(justAfterMidnight.instant())   // first call: range snapshot → today = 2024-03-16
            .thenReturn(justBeforeMidnight.instant())  // must never be used
        `when`(clock.zone).thenReturn(ZoneOffset.UTC)

        val serviceId = UUID.randomUUID()
        val groupId   = UUID.randomUUID()
        `when`(serviceService.getOhGroupIdsForService(serviceId)).thenReturn(listOf(groupId))
        `when`(serviceService.get(serviceId)).thenReturn(Service.create(name = "Bidrag", type = ServiceType.TJENESTE, team = "team"))
        `when`(lookupService.getDisplayDataOrDefault(groupId, LocalDate.of(2024, 3, 15))).thenReturn(
            DisplayDataResult(OpeningHoursDisplayData(openingHours = "08:00-21:00", ruleName = "Weekday", rule = "??.??.???? ? 1-5 08:00-21:00"))
        )
        `when`(lookupService.getDisplayDataOrDefault(groupId, LocalDate.of(2024, 3, 16))).thenReturn(
            DisplayDataResult(OpeningHoursDisplayData(openingHours = "08:00-21:00", ruleName = "Weekday", rule = "??.??.???? ? 1-5 08:00-21:00"))
        )

        // Snapshot today = 2024-03-16 at 00:00:01:
        //   entry 0 (2024-03-15 ≠ today) → open-at-all → 08:00-21:00 ≠ 00:00-00:00 → true
        //   entry 1 (2024-03-16 = today at 00:00:01) → real-time → before 08:00 → false
        //
        // Without the snapshot, if entry 1 re-read the clock and got 23:59:59 (today=2024-03-15):
        //   entry 0 would become today → real-time at 23:59:59 after 21:00 → false  (WRONG)
        //   entry 1 would become not-today → open-at-all → true                     (WRONG)
        mockMvc.get("/api/openinghours/query/service/$serviceId/range?from=2024-03-15&to=2024-03-16")
            .andExpect {
                status { isOk() }
                jsonPath("$.length()") { value(2) }
                jsonPath("$[0].isOpen") { value(true) }   // yesterday → open-at-all
                jsonPath("$[1].isOpen") { value(false) }  // today at 00:00:01 → not yet open
            }
    }

    @Test
    fun `query range clock is read exactly once when snapshot falls just before midnight`() {
        // The clock is captured just before midnight so the range straddles the day boundary.
        // Combining a midnight-boundary snapshot with the exact-once call-count assertion
        // directly exercises the single-instant guarantee: even when the wall-clock would
        // tick to the next day partway through the iteration, the controller must not
        // re-read it, and every entry must be evaluated against the same frozen "today".
        val justBeforeMidnight = Clock.fixed(Instant.parse("2024-03-15T23:59:59Z"), ZoneOffset.UTC)
        `when`(clock.instant()).thenReturn(justBeforeMidnight.instant())
        `when`(clock.zone).thenReturn(ZoneOffset.UTC)

        val serviceId = UUID.randomUUID()
        val groupId   = UUID.randomUUID()
        `when`(serviceService.getOhGroupIdsForService(serviceId)).thenReturn(listOf(groupId))
        `when`(serviceService.get(serviceId)).thenReturn(Service.create(name = "Bidrag", type = ServiceType.TJENESTE, team = "team"))
        listOf(LocalDate.of(2024, 3, 15), LocalDate.of(2024, 3, 16)).forEach { d ->
            `when`(lookupService.getDisplayDataOrDefault(groupId, d)).thenReturn(
                DisplayDataResult(OpeningHoursDisplayData(openingHours = "08:00-21:00", ruleName = "Weekday", rule = "??.??.???? ? 1-5 08:00-21:00"))
            )
        }

        // Snapshot today = 2024-03-15 at 23:59:59 (after the 21:00 close):
        //   entry 0 (2024-03-15 = today) → real-time → after 21:00 → closed
        //   entry 1 (2024-03-16 ≠ today) → open-at-all → 08:00-21:00 → true
        mockMvc.get("/api/openinghours/query/service/$serviceId/range?from=2024-03-15&to=2024-03-16")
            .andExpect {
                status { isOk() }
                jsonPath("$[0].isOpen") { value(false) }  // today at 23:59:59 — after close
                jsonPath("$[1].isOpen") { value(true) }   // tomorrow — open-at-all
            }

        // Structural guarantee: the clock was read exactly once for the whole range,
        // even though the snapshot was taken just one second before the day rolled over.
        verify(clock, times(1)).instant()
    }

    @Test
    fun `query range clock instant is read exactly once regardless of range length`() {
        // The production code calls LocalDateTime.now(clock) once before the date loop.
        // Regardless of how many entries the range contains, clock#instant must be
        // called exactly once — not once per entry.
        val snapshot = Clock.fixed(Instant.parse("2024-03-15T10:00:00Z"), ZoneOffset.UTC)
        `when`(clock.instant()).thenReturn(snapshot.instant())
        `when`(clock.zone).thenReturn(ZoneOffset.UTC)

        val serviceId = UUID.randomUUID()
        val groupId   = UUID.randomUUID()
        `when`(serviceService.getOhGroupIdsForService(serviceId)).thenReturn(listOf(groupId))
        `when`(serviceService.get(serviceId)).thenReturn(Service.create(name = "Bidrag", type = ServiceType.TJENESTE, team = "team"))
        (12..15).forEach { day ->
            `when`(lookupService.getDisplayDataOrDefault(groupId, LocalDate.of(2024, 3, day))).thenReturn(
                DisplayDataResult(OpeningHoursDisplayData(openingHours = "08:00-21:00", ruleName = "Weekday", rule = "??.??.???? ? 1-5 08:00-21:00"))
            )
        }

        mockMvc.get("/api/openinghours/query/service/$serviceId/range?from=2024-03-12&to=2024-03-15")
            .andExpect {
                status { isOk() }
                jsonPath("$.length()") { value(4) }
            }

        // Core assertion: regardless of the 4-entry range, the clock was read only once.
        verify(clock, times(1)).instant()
    }

    @Test
    fun `query range three-day straddle - today in the middle keeps consistent semantics across all entries`() {
        // Range: yesterday | today | tomorrow. Snapshot at 23:59:59 on the middle day (today).
        // If the clock advanced past midnight mid-iteration, yesterday would become not-today
        // (open-at-all) and tomorrow would become today (real-time) — reversing two entries.
        // The single snapshot must prevent that.
        val snapshotBeforeMidnight = Clock.fixed(Instant.parse("2024-03-15T23:59:59Z"), ZoneOffset.UTC)
        val afterMidnight          = Clock.fixed(Instant.parse("2024-03-16T00:00:01Z"), ZoneOffset.UTC)
        `when`(clock.instant())
            .thenReturn(snapshotBeforeMidnight.instant())  // snapshot → today = 2024-03-15
            .thenReturn(afterMidnight.instant())           // must never be used
        `when`(clock.zone).thenReturn(ZoneOffset.UTC)

        val serviceId = UUID.randomUUID()
        val groupId   = UUID.randomUUID()
        `when`(serviceService.getOhGroupIdsForService(serviceId)).thenReturn(listOf(groupId))
        `when`(serviceService.get(serviceId)).thenReturn(Service.create(name = "Bidrag", type = ServiceType.TJENESTE, team = "team"))
        listOf(
            LocalDate.of(2024, 3, 14), // yesterday
            LocalDate.of(2024, 3, 15), // today (snapshot)
            LocalDate.of(2024, 3, 16), // tomorrow
        ).forEach { d ->
            `when`(lookupService.getDisplayDataOrDefault(groupId, d)).thenReturn(
                DisplayDataResult(OpeningHoursDisplayData(openingHours = "08:00-21:00", ruleName = "Weekday", rule = "??.??.???? ? 1-5 08:00-21:00"))
            )
        }

        // Snapshot today = 2024-03-15 at 23:59:59:
        //   entry 0 (2024-03-14 ≠ today) → open-at-all → 08:00-21:00 → true
        //   entry 1 (2024-03-15 = today at 23:59:59) → real-time → after 21:00 → false
        //   entry 2 (2024-03-16 ≠ today) → open-at-all → 08:00-21:00 → true
        //
        // Without the snapshot, if the clock ticked to 00:00:01 after entry 0 was processed:
        //   entry 0 would have been today (2024-03-15 at 23:59:59 → closed) — same by coincidence
        //   entry 1 would become not-today (open-at-all → true)              — WRONG
        //   entry 2 would become today (00:00:01, before 08:00 → false)      — WRONG
        mockMvc.get("/api/openinghours/query/service/$serviceId/range?from=2024-03-14&to=2024-03-16")
            .andExpect {
                status { isOk() }
                jsonPath("$.length()") { value(3) }
                jsonPath("$[0].isOpen") { value(true) }   // yesterday  → open-at-all
                jsonPath("$[1].isOpen") { value(false) }  // today at 23:59:59 → after close
                jsonPath("$[2].isOpen") { value(true) }   // tomorrow   → open-at-all
            }
    }

    // ── redDay: Norwegian public holidays ─────────────────────────────────

    @Test
    fun `query by group returns redDay true on Norwegian public holiday even when rule does not set it`() {
        // 2024-03-31 is Easter Sunday — official Norwegian public holiday.
        // The lookup service returns redDay=false (rule does not mark it), but the
        // controller must override it to true based on the date.
        val groupId = UUID.randomUUID()
        val easter  = LocalDate.of(2024, 3, 31)

        `when`(lookupService.getDisplayData(groupId, easter)).thenReturn(
            OpeningHoursDisplayData(
                openingHours = "00:00-00:00",
                ruleName = "Easter Sunday",
                rule = "??.??.???? ? ? 00:00-00:00",
                redDay = false,
            )
        )

        mockMvc.get("/api/openinghours/query/group/$groupId?date=2024-03-31")
            .andExpect {
                status { isOk() }
                jsonPath("$.redDay") { value(true) }
            }
    }

    @Test
    fun `query by group returns redDay true on Constitution Day (17 May)`() {
        val groupId = UUID.randomUUID()
        val date    = LocalDate.of(2025, 5, 17)

        `when`(lookupService.getDisplayData(groupId, date)).thenReturn(
            OpeningHoursDisplayData(openingHours = "00:00-00:00", ruleName = "17 mai", rule = "17.05.???? ? ? 00:00-00:00", redDay = false)
        )

        mockMvc.get("/api/openinghours/query/group/$groupId?date=2025-05-17")
            .andExpect {
                status { isOk() }
                jsonPath("$.redDay") { value(true) }
            }
    }

    @Test
    fun `query by group returns redDay false on an ordinary weekday`() {
        val groupId = UUID.randomUUID()
        val date    = LocalDate.of(2024, 3, 18) // Monday — not a holiday

        `when`(lookupService.getDisplayData(groupId, date)).thenReturn(
            OpeningHoursDisplayData(openingHours = "08:00-16:00", ruleName = "Weekday", rule = "??.??.???? ? 1-5 08:00-16:00", redDay = false)
        )

        mockMvc.get("/api/openinghours/query/group/$groupId?date=2024-03-18")
            .andExpect {
                status { isOk() }
                jsonPath("$.redDay") { value(false) }
            }
    }

    @Test
    fun `query range includes redDay true for public holidays and false for normal days`() {
        // 2025-04-17 Maundy Thursday, 2025-04-18 Good Friday (both public holidays)
        // 2025-04-16 Wednesday before Easter — ordinary day
        val serviceId = UUID.randomUUID()
        val groupId   = UUID.randomUUID()
        `when`(serviceService.getOhGroupIdsForService(serviceId)).thenReturn(listOf(groupId))
        `when`(serviceService.get(serviceId)).thenReturn(Service.create(name = "Bidrag", type = ServiceType.TJENESTE, team = "team"))

        val wednesday = LocalDate.of(2025, 4, 16)
        val thursday  = LocalDate.of(2025, 4, 17) // Skjærtorsdag
        val friday    = LocalDate.of(2025, 4, 18) // Langfredag

        listOf(wednesday, thursday, friday).forEach { d ->
            `when`(lookupService.getDisplayDataOrDefault(groupId, d)).thenReturn(
                DisplayDataResult(OpeningHoursDisplayData(openingHours = "08:00-16:00", ruleName = "Base", rule = "??.??.???? ? 1-5 08:00-16:00", redDay = false))
            )
        }

        mockMvc.get("/api/openinghours/query/service/$serviceId/range?from=2025-04-16&to=2025-04-18")
            .andExpect {
                status { isOk() }
                jsonPath("$.length()") { value(3) }
                jsonPath("$[0].redDay") { value(false) } // Wednesday — ordinary day
                jsonPath("$[1].redDay") { value(true) }  // Maundy Thursday — public holiday
                jsonPath("$[2].redDay") { value(true) }  // Good Friday — public holiday
            }
    }
}
