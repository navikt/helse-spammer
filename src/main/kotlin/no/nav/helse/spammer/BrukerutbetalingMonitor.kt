package no.nav.helse.spammer

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import no.nav.helse.rapids_rivers.*
import org.slf4j.LoggerFactory

internal class BrukerutbetalingMonitor(
    rapidsConnection: RapidsConnection,
    val slackClient: SlackClient?
) : River.PacketListener {
    private companion object {
        private val sikkerLog = LoggerFactory.getLogger("tjenestekall")
    }

    init {
        River(rapidsConnection).apply {
            validate {
                it.demandValue("@event_name", "utbetaling_endret")
                it.requireAny("type", listOf("UTBETALING", "ANNULLERING", "ETTERUTBETALING", "REVURDERING"))
                it.requireKey("aktørId", "fødselsnummer", "organisasjonsnummer", "utbetalingId")
                it.requireKey("personOppdrag")
                it.requireKey("personOppdrag.nettoBeløp")
                it.requireKey("personOppdrag.linjer")
                it.requireKey("forrigeStatus")
                it.requireKey("gjeldendeStatus")
                it.require("@opprettet", JsonNode::asLocalDateTime)
            }
        }.register(this)
    }

    override fun onError(problems: MessageProblems, context: MessageContext) {
        sikkerLog.error("kunne ikke forstå utbetaling_endret: ${problems.toExtendedReport()}")
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val harIkkeNullNettobeløp = packet["personOppdrag"]["nettoBeløp"].asInt() != 0
        val harLinjerMedEndring =
            (packet["personOppdrag"]["linjer"] as ArrayNode).isNotEmpty() && packet["personOppdrag"]["linjer"].any { it["endringskode"].asText() != "UEND" }
        if(packet["gjeldendeStatus"].asText() == "FORKASTET") return

        if (harIkkeNullNettobeløp || harLinjerMedEndring) {
            val utbetalingId = packet["utbetalingId"].asText()
            val gjeldendeStatus = packet["gjeldendeStatus"].asText()
            val forrigeStatus = packet["forrigeStatus"].asText()

            sikkerLog.info("Utbetaling:$utbetalingId til bruker gikk fra $forrigeStatus til $gjeldendeStatus")
            slackClient?.postMessage("Utbetaling:$utbetalingId til bruker gikk fra $forrigeStatus til $gjeldendeStatus")
        }
    }

    private fun ArrayNode.isNotEmpty() = !this.isEmpty
}
