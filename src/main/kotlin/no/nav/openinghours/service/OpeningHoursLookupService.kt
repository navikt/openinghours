package no.nav.openinghours.service

import no.nav.openinghours.evaluator.OpeningHoursDisplayData
import no.nav.openinghours.evaluator.OpeningHoursEvaluator
import no.nav.openinghours.evaluator.OpeningHoursTreeResolver
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDate
import java.util.UUID

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
}