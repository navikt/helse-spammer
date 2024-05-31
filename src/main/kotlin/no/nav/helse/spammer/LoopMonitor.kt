package no.nav.helse.spammer

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.navikt.tbd_libs.spurtedu.SkjulRequest
import com.github.navikt.tbd_libs.spurtedu.SpurteDuClient
import no.nav.helse.rapids_rivers.*
import org.slf4j.LoggerFactory
import java.time.LocalDateTime

internal class LoopMonitor(
    rapidsConnection: RapidsConnection,
    private val slackClient: SlackClient?,
    private val spurteDuClient: SpurteDuClient
) : River.PacketListener {
    private companion object {
        private val sikkerLog = LoggerFactory.getLogger("tjenestekall")
    }

    init {
        River(rapidsConnection).apply {
            validate { it.demandValue("@event_name", "vedtaksperiode_i_loop") }
            validate { it.requireKey("vedtaksperiodeId", "fødselsnummer", "forrigeTilstand", "gjeldendeTilstand") }
        }.register(this)
    }

    override fun onError(problems: MessageProblems, context: MessageContext) {
        sikkerLog.error("forstod ikke vedtaksperiode_i_loop:\n${problems.toExtendedReport()}")
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val vedtaksperiodeId = packet["vedtaksperiodeId"].asText()
        val forrigeTilstand = packet["forrigeTilstand"].asText()
        val gjeldendeTilstand = packet["gjeldendeTilstand"].asText()

        slackClient?.postMessage(
            String.format(
                "Advarsel: mulig loop oppdaget i vedtaksperiode: <%s|%s> hopper mellom %s og %s. " +
                        "Sjekk <%s|tilstandsmaskinen> eller <%s|spanner>",
                Kibana.createUrl(
                    String.format("\"%s\"", vedtaksperiodeId),
                    LocalDateTime.now().minusDays(30),
                    null,
                    "tjenestekall-*"
                ),
                vedtaksperiodeId,
                forrigeTilstand,
                gjeldendeTilstand,
                "https://sporing.ansatt.nav.no/tilstandsmaskin/${vedtaksperiodeId}",
                spannerlink(spurteDuClient, packet["fødselsnummer"].asText())
            )
        )
    }
}

private val objectMapper: ObjectMapper = jacksonObjectMapper().registerModule(JavaTimeModule())
private const val tbdgruppeProd = "c0227409-2085-4eb2-b487-c4ba270986a3"

fun spannerlink(spurteDuClient: SpurteDuClient, fnr: String): String {
    val payload = SkjulRequest.SkjulTekstRequest(
        tekst = objectMapper.writeValueAsString(mapOf(
            "ident" to fnr,
            "identtype" to "FNR"
        )),
        påkrevdTilgang = tbdgruppeProd
    )

    val spurteDuLink = spurteDuClient.skjul(payload)
    return "https://spanner.ansatt.nav.no/person/${spurteDuLink.id}"
}
