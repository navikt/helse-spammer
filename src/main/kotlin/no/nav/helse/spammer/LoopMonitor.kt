package no.nav.helse.spammer

import com.github.navikt.tbd_libs.personpseudoid.Identitetsnummer
import com.github.navikt.tbd_libs.personpseudoid.PersonPseudoIdProvider
import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageProblems
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.micrometer.core.instrument.MeterRegistry
import io.opentelemetry.api.trace.Span
import org.slf4j.LoggerFactory
import java.net.URLEncoder
import java.nio.charset.StandardCharsets.UTF_8

internal class LoopMonitor(
    rapidsConnection: RapidsConnection,
    private val slackClient: SlackClient?,
    private val personPseudoIdProvider: PersonPseudoIdProvider,
    private val googleCloudProject: String,
    private val speilUrl: String,
) : River.PacketListener {
    private companion object {
        private val sikkerLog = LoggerFactory.getLogger("tjenestekall")
    }

    init {
        River(rapidsConnection)
            .apply {
                precondition { it.requireValue("@event_name", "vedtaksperiode_i_loop") }
                validate { it.requireKey("vedtaksperiodeId", "fødselsnummer", "forrigeTilstand", "gjeldendeTilstand") }
            }.register(this)
    }

    override fun onError(
        problems: MessageProblems,
        context: MessageContext,
        metadata: MessageMetadata,
    ) {
        sikkerLog.error("forstod ikke vedtaksperiode_i_loop:\n${problems.toExtendedReport()}")
    }

    override fun onPacket(
        packet: JsonMessage,
        context: MessageContext,
        metadata: MessageMetadata,
        meterRegistry: MeterRegistry,
    ) {
        val vedtaksperiodeId = packet["vedtaksperiodeId"].asString()
        val forrigeTilstand = packet["forrigeTilstand"].asString()
        val gjeldendeTilstand = packet["gjeldendeTilstand"].asString()
        val spanContext = Span.current().spanContext
        val loggsøk =
            if (spanContext.isValid) {
                """jsonPayload.trace_id="${spanContext.traceId}""""
            } else {
                """"$vedtaksperiodeId""""
            }
        val logglenketekst = if (spanContext.isValid) "Team Logs (trace)" else "Team Logs (vedtaksperiode, mangler trace)"
        val teamLogsUrl =
            "https://console.cloud.google.com/logs/query;query=${URLEncoder.encode(loggsøk, UTF_8)}" +
                ";duration=P30D?project=${URLEncoder.encode(googleCloudProject, UTF_8)}"

        sikkerLog.info("Oppdaget mulig loop i vedtaksperiode: {}", vedtaksperiodeId)

        slackClient?.postMessage(
            "Advarsel: mulig loop oppdaget i vedtaksperiode: $vedtaksperiodeId hopper mellom $forrigeTilstand og $gjeldendeTilstand. " +
                "Sjekk <$teamLogsUrl|$logglenketekst>, " +
                "<https://sporing.ansatt.nav.no/tilstandsmaskin/$vedtaksperiodeId|tilstandsmaskinen> eller " +
                "<${spannerlink(personPseudoIdProvider, packet["fødselsnummer"].asString())}|spanner>. " +
                "<$speilUrl/vedtaksperiode/$vedtaksperiodeId|Åpne i Speil>",
        )
    }
}

fun spannerlink(
    personPseudoIdProvider: PersonPseudoIdProvider,
    fnr: String,
): String {
    val personPseudoId = personPseudoIdProvider.nyPersonPseudoId(Identitetsnummer(fnr))
    return "https://spanner.ansatt.nav.no/person/$personPseudoId"
}
