package no.nav.openinghours.validator

/**
 * Resolves which calendar year a rule is anchored to, based on the date field (part 1 of 4)
 * of the rule DSL.
 *
 * The date field has four forms (see [RuleValidator]):
 * - `??.??.????` — matches every date, belongs to no year
 * - `dd.MM.????` — recurs every year on dd.MM, belongs to no year
 * - `??.MM.????` — recurs every year in month MM, belongs to no year
 * - `dd.MM.yyyy` — a single calendar date, belongs to year yyyy
 *
 * A concrete year in strict `yyyy` format (exactly four digits, e.g. `2024`) is what ties a rule
 * to a year and makes it deletable. The year-anchored wildcard forms `??.MM.yyyy` and `??.??.yyyy`
 * cannot be created through the API today, but legacy or hand-inserted rows may contain them, so
 * they are recognised here as belonging to their year.
 */
object RuleExpiry {

    /** Years outside this range are not plausible `yyyy` calendar years and are rejected. */
    val SUPPORTED_YEARS: IntRange = 1000..9999

    private val YYYY = Regex("\\d{4}")

    /**
     * Returns the year [ruleDsl] is anchored to, or `null` when the rule recurs every year or the
     * date field is malformed. A `null` result means the rule belongs to no single year, and such
     * rules must never be deleted as outdated.
     *
     * The year field must be a strict `yyyy` value: exactly four digits. Anything else — the
     * recurring wildcard `????`, a signed or padded number that [String.toIntOrNull] would
     * otherwise accept (`+204`, `-204`), or a two-digit year — is not datable.
     */
    fun anchorYear(ruleDsl: String): Int? {
        val datePart = ruleDsl.trim().split(Regex("\\s+")).firstOrNull() ?: return null
        val yearToken = datePart.split(".").takeIf { it.size == 3 }?.get(2) ?: return null
        return yearToken.takeIf { YYYY.matches(it) }?.toInt()
    }

    /**
     * True when [ruleDsl] is anchored to exactly [year]. Recurring and malformed rules belong to
     * no year and are therefore never matched.
     */
    fun belongsToYear(ruleDsl: String, year: Int): Boolean = anchorYear(ruleDsl) == year
}
