package no.nav.helse.spammer

import io.mockk.CapturingSlot
import io.mockk.mockk
import io.mockk.verify
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class BrukerutbetalingMonitorTest {

    private val rapid = TestRapid()
    private val slackClientMock = mockk<SlackClient>(relaxed = true)
    private val utgåendeMelding: CapturingSlot<String> = CapturingSlot()

    init {
        BrukerutbetalingMonitor(rapid, slackClientMock)
    }

    @Test
    fun `varsler om brukerutbetaling er utbetalt`() {
        rapid.sendTestMessage(utbetalingMedLinjer(forrigeStatus = "OVERFØRT", gjeldendeStatus = "UTBETALT"))
        verify(exactly = 1) { slackClientMock.postMessage(text = capture(utgåendeMelding)) }
        assertEquals("Utbetaling:utbetalingId til bruker gikk fra OVERFØRT til UTBETALT", utgåendeMelding.captured)
    }

    @Test
    fun `varsler ikke om brukerutbetaling går til annen status enn utbetalt`() {
        rapid.sendTestMessage(utbetalingMedLinjer(forrigeStatus = "SENDT", gjeldendeStatus = "OVERFØRT"))
        verify(exactly = 0) { slackClientMock.postMessage(text = capture(utgåendeMelding)) }
    }

    @Test
    fun `varsler om annullering av brukerutbetaling er utbetalt`() {
        rapid.sendTestMessage(annullering(forrigeStatus = "OVERFØRT", gjeldendeStatus = "UTBETALT"))
        verify(exactly = 1) { slackClientMock.postMessage(text = capture(utgåendeMelding)) }
        assertEquals("Utbetaling:utbetalingId til bruker gikk fra OVERFØRT til UTBETALT", utgåendeMelding.captured)
    }

    @Test
    fun `varsler ikke om brukerutbetaling ved UEND i linjer`() {
        rapid.sendTestMessage(utbetalingMedLinjer(endringskode = "UEND", nettobeløp = 0, forrigeStatus = "OVERFØRT", gjeldendeStatus = "UTBETALT"))
        verify(exactly = 0) { slackClientMock.postMessage(any()) }
    }

    @Test
    fun `varsler ikke om brukerutbetaling ved tomme linjer`() {
        rapid.sendTestMessage(utbetalingUtenLinjer())
        verify(exactly = 0) { slackClientMock.postMessage(any()) }
    }

    @Language("Json")
    fun utbetalingMedLinjer(
        endringskode: String = "NY",
        nettobeløp: Int = 1338,
        forrigeStatus: String,
        gjeldendeStatus: String
    ) = """{
  "utbetalingId": "utbetalingId",
  "type": "UTBETALING",
  "forrigeStatus": "$forrigeStatus",
  "gjeldendeStatus": "$gjeldendeStatus",
  "personOppdrag": {
    "fagsystemId": "FAGSYSTEMID",
    "nettoBeløp": $nettobeløp,
    "linjer": [
      {
        "fom": "2021-01-01",
        "tom": "2021-01-31",
        "endringskode": "$endringskode"
      }
    ]
  },
  "@event_name": "utbetaling_endret",
  "@id": "4450173a-8161-439c-a8e4-b3342d173122",
  "@opprettet": "2021-11-19T06:45:38.45364452",
  "fødselsnummer": "fødselsnummer",
  "aktørId": "aktørId",
  "organisasjonsnummer": "orgnummer"
}
    """

    @Language("Json")
    fun annullering(
        forrigeStatus: String,
        gjeldendeStatus: String
    ) = """{
  "utbetalingId": "utbetalingId",
  "type": "ANNULLERING",
  "forrigeStatus": "$forrigeStatus",
  "gjeldendeStatus": "$gjeldendeStatus",
  "personOppdrag": {
    "fagsystemId": "FAGSYSTEMID",
    "nettoBeløp": 0,
    "linjer": [
      {
        "fom": "2021-01-01",
        "tom": "2021-01-31",
        "endringskode": "ENDR",
        "datoStatusFom": "2021-01-01"
      }
    ]
  },
  "@event_name": "utbetaling_endret",
  "@id": "4450173a-8161-439c-a8e4-b3342d173122",
  "@opprettet": "2021-11-19T06:45:38.45364452",
  "fødselsnummer": "fødselsnummer",
  "aktørId": "aktørId",
  "organisasjonsnummer": "orgnummer"
}
    """


    @Language("Json")
    fun utbetalingUtenLinjer() = """{
  "utbetalingId": "utbetalingId",
  "type": "UTBETALING",
  "forrigeStatus": "OVERFØRT",
  "gjeldendeStatus": "UTBETALT",
  "personOppdrag": {
    "fagsystemId": "FAGSYSTEMID",
    "nettoBeløp": 0,
    "linjer": []
  },
  "@event_name": "utbetaling_endret",
  "@id": "4450173a-8161-439c-a8e4-b3342d173122",
  "@opprettet": "2021-11-19T06:45:38.45364452",
  "fødselsnummer": "fødselsnummer",
  "aktørId": "aktørId",
  "organisasjonsnummer": "orgnummer"
}
    """

}