package no.nav.openinghours.evaluator

data class OpeningHoursDisplayData(
    val ruleName: String? = null,
    val rule: String? = null,
    val openingHours: String? = null,
    val displayHeader: String? = null,
    val displayText: String? = null,
    val onlyShowForNavEmployees: Boolean = false,
)
