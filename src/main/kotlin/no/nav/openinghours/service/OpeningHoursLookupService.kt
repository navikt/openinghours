package no.nav.openinghours.service

import no.nav.openinghours.evaluator.OpeningHoursDisplayData
import no.nav.openinghours.evaluator.OpeningHoursEvaluator
import no.nav.openinghours.evaluator.OpeningHoursTreeResolver
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDate
import java.util.UUID

/**
 * Wraps [OpeningHoursDisplayData] with an optional [warningMessage] that is populated when no
 * rule matched the requested date and the default display data was substituted as a fallback.
 */
data class DisplayDataResult(
    val data: OpeningHoursDisplayData,
    val warningMessage: String? = null,
)

@Service
class OpeningHoursLookupService(
    private val resolver: OpeningHoursTreeResolver,
    private val evaluator: OpeningHoursEvaluator,
) {
    fun getOpeningHours(groupId: UUID, date: LocalDate): String =
        evaluator.getOpeningHours(date, resolver.resolve(groupId))

    fun getDisplayData(groupId: UUID, date: LocalDate): OpeningHoursDisplayData {
        val group = resolver.resolve(groupId)
        return evaluator.getDisplayData(date, group)
            ?: throw ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Group '$groupId' has rules defined, but none match the requested date: $date"
            )
    }

    /**
     * Like [getDisplayData] but never throws on a no-match: instead it returns
     * [OpeningHoursEvaluator.DEFAULT_DISPLAY_DATA] together with a [DisplayDataResult.warningMessage]
     * that describes the situation. Used by range queries so that the full date range is always
     * returned even when some dates have no applicable rule.
     */
    fun getDisplayDataOrDefault(groupId: UUID, date: LocalDate): DisplayDataResult {
        val group = resolver.resolve(groupId)
        val data = evaluator.getDisplayData(date, group)
        return if (data != null) {
            DisplayDataResult(data)
        } else {
            DisplayDataResult(
                data = OpeningHoursEvaluator.DEFAULT_DISPLAY_DATA,
                warningMessage = "Group '$groupId' has rules defined, but none match the requested date: $date. Returned default display data.",
            )
        }
    }
}