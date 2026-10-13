package no.nav.helse.spammer

import com.github.navikt.tbd_libs.personpseudoid.Identitetsnummer
import com.github.navikt.tbd_libs.personpseudoid.PersonPseudoId
import com.github.navikt.tbd_libs.personpseudoid.PersonPseudoIdProvider
import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.mockk.CapturingSlot
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

internal class LoopMonitorTest {
    private val rapid = TestRapid()
    private val slackClientMock = mockk<SlackClient>(relaxed = true)
    private val utgåendeMelding: CapturingSlot<String> = CapturingSlot()

    private val personPseudoId = PersonPseudoId(UUID.fromString("5a1b8c2e-3f4d-4e6a-9b7c-8d9e0f1a2b3c"))
    private val personPseudoIdProvider =
        object : PersonPseudoIdProvider {
            override fun nyPersonPseudoId(identitetsnummer: Identitetsnummer) = personPseudoId

            override fun finnIdentitetsnummer(personPseudoId: PersonPseudoId): Identitetsnummer? = null
        }

    init {
        LoopMonitor(rapid, slackClientMock, personPseudoIdProvider)
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
        assertTrue(melding.contains("https://spanner.ansatt.nav.no/person/$personPseudoId"))
    }

    val melding = """
        {
          "@event_name": "vedtaksperiode_i_loop",
          "@opprettet": "2021-04-28T11:15:17.013909",
          "@id": "df05431b-8909-4e00-8c90-04f9f90d95d8",
          "vedtaksperiodeId": "77033b13-1f07-4b1a-92b5-4f6cce88da8a",
          "fødselsnummer": "12345678910",
          "forrigeTilstand": "AVVENTER_INNTEKTSMELDING_ELLER_HISTORIKK_FERDIG_GAP",
          "gjeldendeTilstand": "AVVENTER_INNTEKTSMELDING_UFERDIG_GAP",
          "system_read_count": 0
        }
    """
}
