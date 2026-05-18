package no.nav.openinghours.service

import no.nav.openinghours.evaluator.OpeningHoursDisplayData
import no.nav.openinghours.evaluator.OpeningHoursEvaluator
import no.nav.openinghours.evaluator.OpeningHoursTreeResolver
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.util.UUID

@Service
class OpeningHoursLookupService(
    private val resolver: OpeningHoursTreeResolver,
    private val evaluator: OpeningHoursEvaluator,
) {
    fun getOpeningHours(groupId: UUID, date: LocalDate): String =
        evaluator.getOpeningHours(date, resolver.resolve(groupId))

    fun getDisplayData(groupId: UUID, date: LocalDate): OpeningHoursDisplayData =
        evaluator.getDisplayData(date, resolver.resolve(groupId))
}