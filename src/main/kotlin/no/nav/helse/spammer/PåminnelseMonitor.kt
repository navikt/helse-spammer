package no.nav.helse.spammer

import com.fasterxml.jackson.databind.JsonNode
import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers.asLocalDateTime
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import com.github.navikt.tbd_libs.spurtedu.SpurteDuClient
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import kotlin.time.ExperimentalTime

@ExperimentalTime
internal class PåminnelseMonitor(
    rapidsConnection: RapidsConnection,
    slackClient: SlackClient?,
    slackThreadDao: SlackThreadDao?,
    spurteDuClient: SpurteDuClient
) {
    private companion object {
        private val sikkerLog = LoggerFactory.getLogger("tjenestekall")
        private val interessanteTilstander = setOf(
            "AVVENTER_VILKÅRSPRØVING",
            "AVVENTER_VILKÅRSPRØVING_REVURDERING",
            "AVVENTER_HISTORIKK_REVURDERING",
            "AVVENTER_HISTORIKK",
            "AVVENTER_SIMULERING_REVURDERING",
            "AVVENTER_SIMULERING",
            "TIL_UTBETALING"
        )
    }

    init {
        River(rapidsConnection).apply {
            validate { it.demandValue("@event_name", "påminnelse") }
            validate { it.require("@opprettet", JsonNode::asLocalDateTime) }
            validate { it.requireKey("fødselsnummer") }
            validate { it.requireKey("vedtaksperiodeId") }
            validate { it.requireAny("tilstand", interessanteTilstander.toList()) }
            validate { it.requireKey("antallGangerPåminnet") }
        }.register(Påminnelser(slackClient, slackThreadDao, spurteDuClient))
    }

    private class Påminnelser(private val slackClient: SlackClient?, private val slackThreadDao: SlackThreadDao?, private val spurteDuClient: SpurteDuClient): River.PacketListener {
        override fun onPacket(packet: JsonMessage, context: MessageContext, metadata: MessageMetadata, meterRegistry: MeterRegistry) {
            if (slackThreadDao == null) return
            val antallGangerPåminnet = packet["antallGangerPåminnet"].asInt()
            // sørger for å lage en alarm for hver 20. påminnelse
            if (antallGangerPåminnet == 0 || antallGangerPåminnet % 20 != 0) return

            slackClient?.postMessage(
                slackThreadDao,
                packet["vedtaksperiodeId"].asText(),
                String.format(
                    "Vedtaksperiode <%s|%s> (<%s|tjenestekall>) har blitt påminnet %d ganger i %s (<%s|spanner>)!",
                    Kibana.createUrl(String.format("\"%s\"", packet["vedtaksperiodeId"].asText()), packet["@opprettet"].asLocalDateTime().minusHours(1)),
                    packet["vedtaksperiodeId"].asText(),
                    Kibana.createUrl(
                        String.format("\"%s\"", packet["vedtaksperiodeId"].asText()),
                        packet["@opprettet"].asLocalDateTime().minusHours(1),
                        null,
                        "tjenestekall-*"
                    ),
                    antallGangerPåminnet,
                    packet["tilstand"].asText(),
                    spannerlink(spurteDuClient, packet["fødselsnummer"].asText())
                )
            )
        }
    }
}
