package no.nav.openinghours.downtime

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SlackTextTest {

    @Test
    fun `removes Slack markup but keeps readable text and links`() {
        val raw = "Hei <!here> :wrench: Tableau  tas ned kl 21.\n\n\n\nSe <https://nav.no/status|status> og <https://jira.test/A-1> " +
            "i <#C123|drift> T.O: <@U1|ola> (edited)"

        assertThat(SlackText.clean(raw)).isEqualTo(
            "Hei Tableau tas ned kl 21.\n\nSe status og https://jira.test/A-1 i #drift T.O:"
        )
    }
}
