package no.nav.openinghours.downtime

/** Turns Slack markup into plain text for activity messages. */
object SlackText {
    private val USER_MENTION = Regex("<@[A-Z0-9]+(\\|[^>]*)?>")
    private val SPECIAL_MENTION = Regex("<!(here|channel|everyone)[^>]*>")
    private val CHANNEL = Regex("<#[A-Z0-9]+\\|([^>]*)>")
    private val LABELLED_LINK = Regex("<(https?://[^|>]+)\\|([^>]+)>")
    private val LINK = Regex("<(https?://[^>]+)>")
    private val EMOJI = Regex(":[a-z][a-z0-9_+\\-]*:")
    private val EDITED = Regex("\\(edited\\)")

    fun clean(text: String): String = text
        .replace(USER_MENTION, "")
        .replace(SPECIAL_MENTION, "")
        .replace(CHANNEL, "#$1")
        .replace(LABELLED_LINK, "$2")
        .replace(LINK, "$1")
        .replace(EMOJI, "")
        .replace(EDITED, "")
        .lines()
        .joinToString("\n") { it.replace(Regex("[ \\t]+"), " ").trim() }
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()
}
