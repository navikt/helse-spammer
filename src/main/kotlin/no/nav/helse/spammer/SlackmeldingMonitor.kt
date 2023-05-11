package no.nav.helse.spammer

import no.nav.helse.rapids_rivers.*
import org.slf4j.LoggerFactory

internal class SlackmeldingMonitor(
    rapidsConnection: RapidsConnection,
    private val slackClient: SlackClient?
) : River.PacketListener {
    private companion object {
        private val sikkerLog = LoggerFactory.getLogger("tjenestekall")
    }

    init {
        River(rapidsConnection).apply {
            validate {
                it.demandValue("@event_name", "slackmelding")
                it.requireKey("melding")
                it.interestedIn("@avsender.navn", "system_participating_services")
            }
        }.register(this)
    }

    override fun onError(problems: MessageProblems, context: MessageContext) {
        sikkerLog.error("forstod ikke slackmelding:\n${problems.toExtendedReport()}")
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val melding = packet["melding"].asText()
        slackClient?.postMessage("Hei, ${packet.navn} her :meow_wave: $melding")
    }

    private val JsonMessage.navn get(): String {
        val person = get("@avsender.navn").takeUnless { it.isMissingOrNull() }?.asText()?.split(" ")?.last()
        if (person != null) return person
        val app = get("system_participating_services").takeUnless { it.isMissingOrNull() }?.map { it.path("service").asText() }?.last()
        if (app != null) return app
        return "hemmelig beundrer"
    }
}
