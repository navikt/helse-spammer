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
import org.slf4j.LoggerFactory
import java.time.LocalDateTime

internal class LoopMonitor(
    rapidsConnection: RapidsConnection,
    private val slackClient: SlackClient?,
    private val personPseudoIdProvider: PersonPseudoIdProvider,
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

        slackClient?.postMessage(
            String.format(
                "Advarsel: mulig loop oppdaget i vedtaksperiode: <%s|%s> hopper mellom %s og %s. " +
                    "Sjekk <%s|tilstandsmaskinen> eller <%s|spanner>",
                Kibana.createUrl(
                    String.format("\"%s\"", vedtaksperiodeId),
                    LocalDateTime.now().minusDays(30),
                    null,
                    "tjenestekall-*",
                ),
                vedtaksperiodeId,
                forrigeTilstand,
                gjeldendeTilstand,
                "https://sporing.ansatt.nav.no/tilstandsmaskin/$vedtaksperiodeId",
                spannerlink(personPseudoIdProvider, packet["fødselsnummer"].asString()),
            ),
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
