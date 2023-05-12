package no.nav.helse.spammer

import no.nav.helse.rapids_rivers.*
import org.slf4j.LoggerFactory

internal class SlackmeldingMonitor(
    rapidsConnection: RapidsConnection,
    private val slackClient: SlackClient?,
    private val slackAlertsClient: SlackClient?
) : River.PacketListener {

    init {
        River(rapidsConnection).apply {
            validate {
                it.demandValue("@event_name", "slackmelding")
                it.requireKey("melding")
                it.interestedIn("@avsender.navn", "@avsender.epost", "system_participating_services", "level")
            }
        }.register(this)
    }

    override fun onError(problems: MessageProblems, context: MessageContext) {
        sikkerLog.error("forstod ikke slackmelding:\n${problems.toExtendedReport()}")
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val melding = packet["melding"].asText()
        packet.client?.postMessage("${packet.emoji} ${packet.prefix} ${melding}${packet.suffix}")
    }

    private val JsonMessage.client get() = if (error) slackAlertsClient else slackClient

    private companion object {
        private val sikkerLog = LoggerFactory.getLogger("tjenestekall")
        private val String.fintNavn get() = if (length < 2) uppercase() else substring(0, 1).uppercase() + substring(1)

        private val JsonMessage.person get() = get("@avsender.navn").takeUnless { it.isMissingOrNull() }?.asText()?.split(" ")?.lastOrNull()?.fintNavn
        private val JsonMessage.prefix get(): String {
            if (person != null) return "Hei! $person her :meow_wave:"
            val apper = get("system_participating_services").takeUnless { it.isMissingOrNull() }?.map { it.path("service").asText() }?.filterNot { it == "spammer" }?.distinct() ?: emptyList()
            if (apper.isEmpty()) return "Hei! En hemmelig beundrer her :meow_blush:"
            if (apper.size == 1) return "Hei! ${apper.single().fintNavn} her :robot_face:"
            val meg = apper.last().fintNavn
            val godVenn = apper[apper.size - 2].fintNavn
            return "Hei! $meg her, min gode venn $godVenn minnet meg på en ting :robot_face:"
        }

        private val JsonMessage.epost get() = get("@avsender.epost").asText()
        private val JsonMessage.suffix get() = if (person == null) "" else ". Om du ønsker å ta dette privat, <svar meg på mail da vel!|mailto:$epost>"
        private val JsonMessage.error get() = get("level").asText().uppercase() == "ERROR"
        private val JsonMessage.emoji get() = if (error) ":alert:" else ":speech_balloon:"
    }
}