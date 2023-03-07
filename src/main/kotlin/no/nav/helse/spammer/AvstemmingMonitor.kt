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

    private val tidsstempel = DateTimeFormatter.ofPattern("eeee d. MMMM")

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
        val fagområde = when (val forkortelsen = packet["fagområde"].asText()) {
            "SP" -> "brukerutbetalinger ($forkortelsen)"
            "SPREF" -> "arbeidsgiverrefusjoner ($forkortelsen)"
            else -> forkortelsen
        }
        slackClient?.postMessage(String.format(
            ":bank: Avstemming for *%s*: %d oppdrag frem til %s ble avstemt, <%s|for %s siden>.",
            fagområde,
            packet["antall_oppdrag"].asInt(),
            packet["dagen"].asLocalDate().format(tidsstempel),
            Kibana.createUrl(String.format("\"%s\"", packet["@id"].asText()), packet["@opprettet"].asLocalDateTime().minusHours(1)),
            humanReadableTime(ChronoUnit.SECONDS.between(packet["@opprettet"].asLocalDateTime(), LocalDateTime.now())),
        ))
    }
}
