package no.nav.helse.spammer

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.mockk.CapturingSlot
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class LoopMonitorTest {

    private val rapid = TestRapid()
    private val slackClientMock = mockk<SlackClient>(relaxed = true)
    private val utgåendeMelding: CapturingSlot<String> = CapturingSlot()

    init {
        LoopMonitor(rapid, slackClientMock, mockk(relaxed = true))
    }

    @BeforeEach
    fun setup() {
        utgåendeMelding.clear()
    }

    @Test
    fun `publiserer utgående slackmelding ved melding om loop`() {
        rapid.sendTestMessage(melding)
        verify { slackClientMock.postMessage(capture(utgåendeMelding)) }
        assertTrue(utgåendeMelding.isCaptured)
    }

    @Test
    fun `melding inneholder vedtaksperiode, tilstander`() {
        rapid.sendTestMessage(melding)
        verify { slackClientMock.postMessage(capture(utgåendeMelding)) }
        val melding = utgåendeMelding.captured
        assertTrue(melding.contains("77033b13-1f07-4b1a-92b5-4f6cce88da8a"))
        assertTrue(melding.contains("AVVENTER_INNTEKTSMELDING_ELLER_HISTORIKK_FERDIG_GAP"))
        assertTrue(melding.contains("AVVENTER_INNTEKTSMELDING_UFERDIG_GAP"))
    }

    val melding = """
        {
          "@event_name": "vedtaksperiode_i_loop",
          "@opprettet": "2021-04-28T11:15:17.013909",
          "@id": "df05431b-8909-4e00-8c90-04f9f90d95d8",
          "vedtaksperiodeId": "77033b13-1f07-4b1a-92b5-4f6cce88da8a",
          "fødselsnummer": "123456789",
          "forrigeTilstand": "AVVENTER_INNTEKTSMELDING_ELLER_HISTORIKK_FERDIG_GAP",
          "gjeldendeTilstand": "AVVENTER_INNTEKTSMELDING_UFERDIG_GAP",
          "system_read_count": 0
        }
    """
}
