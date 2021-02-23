package no.nav.helse.spammer

import no.nav.helse.rapids_rivers.*
import org.slf4j.LoggerFactory
import kotlin.time.ExperimentalTime

@ExperimentalTime
internal class UtbetalingMonitor(
    rapidsConnection: RapidsConnection,
    slackClient: SlackClient?,
    slackThreadDao: SlackThreadDao?
) {
    private companion object {
        private val sikkerLog = LoggerFactory.getLogger("tjenestekall")
    }

    init {
        River(rapidsConnection).apply {
            validate { it.demandValue("@event_name", "vedtaksperiode_endret") }
            validate { it.requireKey("aktørId") }
            validate { it.requireKey("fødselsnummer") }
            validate { it.requireKey("organisasjonsnummer") }
            validate { it.requireKey("vedtaksperiodeId") }
            validate { it.requireKey("@opprettet") }
            validate { it.demandValue("gjeldendeTilstand", "UTBETALING_FEILET") }
        }.register(UtbetalingFeilet(slackClient, slackThreadDao))
    }

    private class UtbetalingFeilet(private val slackClient: SlackClient?, private val slackThreadDao: SlackThreadDao?): River.PacketListener {
        override fun onError(problems: MessageProblems, context: MessageContext) {
            sikkerLog.error("forstod ikke utbetaling_feilet:\n${problems.toExtendedReport()}")
        }

        override fun onPacket(packet: JsonMessage, context: MessageContext) {
            if (slackThreadDao == null) return
            slackClient?.postMessage(
                slackThreadDao,
                packet["vedtaksperiodeId"].asText(),
                String.format(
                    "Utbetaling for vedtaksperiode <%s|%s> (<%s|tjenestekall>) feilet!",
                    Kibana.createUrl(String.format("\"%s\"", packet["vedtaksperiodeId"].asText()), packet["@opprettet"].asLocalDateTime().minusHours(1)),
                    packet["vedtaksperiodeId"].asText(),
                    Kibana.createUrl(
                        String.format("\"%s\"", packet["vedtaksperiodeId"].asText()),
                        packet["@opprettet"].asLocalDateTime().minusHours(1),
                        null,
                        "tjenestekall-*"
                    )
                )
            )
        }
    }
}
