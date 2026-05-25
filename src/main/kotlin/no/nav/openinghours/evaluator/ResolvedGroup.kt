package no.nav.openinghours.evaluator

sealed interface ResolvedEntry {
    val name: String
}

data class ResolvedRule(
    override val name: String,
    val rule: String,
    val displayHeader: String? = null,
    val displayText: String? = null,
    val onlyShowForNavEmployees: Boolean = false,
) : ResolvedEntry

data class ResolvedGroup(
    override val name: String,
    val entries: List<ResolvedEntry>,
) : ResolvedEntry