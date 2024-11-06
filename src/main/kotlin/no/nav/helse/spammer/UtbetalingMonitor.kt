package no.nav.helse.spammer

import com.fasterxml.jackson.databind.JsonNode
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
            validate { it.demandValue("@event_name", "transaksjon_status") }
            validate { it.require("@opprettet", JsonNode::asLocalDateTime) }
            validate { it.requireKey("utbetalingId", "beskrivelse") }
            validate { it.interestedIn("kodemelding") }
            validate { it.requireAny("status", listOf("AVVIST", "FEIL")) }
        }.register(UtbetalingFeilet(slackClient, slackThreadDao))
    }

    private class UtbetalingFeilet(private val slackClient: SlackClient?, private val slackThreadDao: SlackThreadDao?): River.PacketListener {
        override fun onError(problems: MessageProblems, context: MessageContext) {
            sikkerLog.error("forstod ikke transaksjon_status:\n${problems.toExtendedReport()}")
        }

        override fun onPacket(packet: JsonMessage, context: MessageContext) {
            if (slackThreadDao == null) return
            slackClient?.postMessage(
                String.format(
                    "Utbetaling <%s|%s> (<%s|tjenestekall>) feilet med status %s! Beskrivelse: %s%s)",
                    Kibana.createUrl(String.format("\"%s\"", packet["utbetalingId"].asText()), packet["@opprettet"].asLocalDateTime().minusHours(1)),
                    packet["utbetalingId"].asText(),
                    Kibana.createUrl(String.format("\"%s\"", packet["vedtaksperiodeId"].asText()), packet["@opprettet"].asLocalDateTime().minusHours(1), null, "tjenestekall-*"),
                    packet["status"].asText(),
                    packet["beskrivelse"].asText(),
                    packet["kodemelding"].asText()?.let { " ($it)" }
                )
            )
        }
    }
}
