package no.nav.helse.spammer

import no.nav.helse.rapids_rivers.*
import org.slf4j.LoggerFactory
import java.time.LocalDateTime

internal class LoopMonitor(
    rapidsConnection: RapidsConnection,
    private val slackClient: SlackClient?
) : River.PacketListener {
    private companion object {
        private val sikkerLog = LoggerFactory.getLogger("tjenestekall")
    }

    init {
        River(rapidsConnection).apply {
            validate { it.demandValue("@event_name", "vedtaksperiode_i_loop") }
            validate { it.requireKey("vedtaksperiodeId", "forrigeTilstand", "gjeldendeTilstand") }
        }.register(this)
    }

    override fun onError(problems: MessageProblems, context: MessageContext) {
        sikkerLog.error("forstod ikke utbetaling_feilet:\n${problems.toExtendedReport()}")
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val vedtaksperiodeId = packet["vedtaksperiodeId"].asText()
        val forrigeTilstand = packet["forrigeTilstand"].asText()
        val gjeldendeTilstand = packet["gjeldendeTilstand"].asText()
        slackClient?.postMessage(
            String.format(
                "Advarsel: mulig loop oppdaget i vedtaksperiode: <%s|%s> hopper mellom %s og %s. " +
                        "Sjekk <%s|tilstandsmaskinen>",
                Kibana.createUrl(
                    String.format("\"%s\"", vedtaksperiodeId),
                    LocalDateTime.now().minusDays(30),
                    null,
                    "tjenestekall-*"
                ),
                vedtaksperiodeId,
                forrigeTilstand, gjeldendeTilstand,
                "https://sporing.intern.nav.no/tilstandsmaskin/${vedtaksperiodeId}"
            )
        )
    }
}
