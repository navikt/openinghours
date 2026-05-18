package no.nav.openinghours.evaluator

sealed interface ResolvedEntry {
    val name: String
}

data class ResolvedRule(
    override val name: String,
    val rule: String,
) : ResolvedEntry

data class ResolvedGroup(
    override val name: String,
    val entries: List<ResolvedEntry>,
) : ResolvedEntry