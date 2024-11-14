package no.nav.helse.spammer

import com.fasterxml.jackson.databind.JsonNode
import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers.asLocalDateTime
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageProblems
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.micrometer.core.instrument.MeterRegistry
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
            precondition {
                it.requireValue("@event_name", "transaksjon_status")
                it.requireAny("status", listOf("AVVIST", "FEIL"))
            }
            validate { it.require("@opprettet", JsonNode::asLocalDateTime) }
            validate { it.requireKey("utbetalingId", "beskrivelse") }
            validate { it.interestedIn("kodemelding") }
        }.register(UtbetalingFeilet(slackClient, slackThreadDao))
    }

    private class UtbetalingFeilet(private val slackClient: SlackClient?, private val slackThreadDao: SlackThreadDao?): River.PacketListener {
        override fun onError(problems: MessageProblems, context: MessageContext, metadata: MessageMetadata) {
            sikkerLog.error("forstod ikke transaksjon_status:\n${problems.toExtendedReport()}")
        }

        override fun onPacket(packet: JsonMessage, context: MessageContext, metadata: MessageMetadata, meterRegistry: MeterRegistry) {
            if (slackThreadDao == null) return
            slackClient?.postMessage(
                String.format(
                    "Utbetaling <%s|%s> (<%s|tjenestekall>) feilet med status %s! Beskrivelse: %s%s)",
                    Kibana.createUrl(String.format("\"%s\"", packet["utbetalingId"].asText()), packet["@opprettet"].asLocalDateTime().minusHours(1)),
                    packet["utbetalingId"].asText(),
                    Kibana.createUrl(String.format("\"%s\"", packet["utbetalingId"].asText()), packet["@opprettet"].asLocalDateTime().minusHours(1), null, "tjenestekall-*"),
                    packet["status"].asText(),
                    packet["beskrivelse"].asText(),
                    packet["kodemelding"].asText()?.let { " ($it)" }
                )
            )
        }
    }
}
