package no.nav.helse.spammer

import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.rapids_rivers.*
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

internal class AvstemmingMonitor(
    rapidsConnection: RapidsConnection,
    private val slackClient: SlackClient?
) : River.PacketListener {

    private companion object {
        private val sikkerLog = LoggerFactory.getLogger("tjenestekall")
    }

    private val tidsstempel = DateTimeFormatter.ofPattern("eeee d. MMMM Y")

    init {
        River(rapidsConnection).apply {
            validate { it.demandValue("@event_name", "avstemming") }
            validate { it.requireKey("@id", "antall_oppdrag", "fagområde") }
            validate { it.require("dagen", JsonNode::asLocalDate) }
            validate { it.requireKey("detaljer.nøkkel_fom", "detaljer.nøkkel_tom", "detaljer.antall_oppdrag", "detaljer.antall_avstemmingsmeldinger") }
            validate { it.require("@opprettet", JsonNode::asLocalDateTime) }
        }.register(this)
    }

    override fun onError(problems: MessageProblems, context: MessageContext) {
        sikkerLog.error("forstod ikke avstemming:\n${problems.toExtendedReport()}")
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        slackClient?.postMessage(String.format(
            "Avstemming <%s|%s> for :calendar: %s for fagområde **%s** ble kjørt for %s siden. :chart_with_upwards_trend: %d oppdrag ble avstemt.",
            Kibana.createUrl(String.format("\"%s\"", packet["@id"].asText()), packet["@opprettet"].asLocalDateTime().minusHours(1)),
            packet["@id"].asText(),
            packet["dagen"].asLocalDate().format(tidsstempel),
            packet["fagområde"].asText(),
            humanReadableTime(ChronoUnit.SECONDS.between(packet["@opprettet"].asLocalDateTime(), LocalDateTime.now())),
            packet["antall_oppdrag"].asInt()
        ))
    }
}
