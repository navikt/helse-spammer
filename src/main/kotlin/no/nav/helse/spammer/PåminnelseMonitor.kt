package no.nav.helse.spammer

import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.rapids_rivers.*
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
            validate { it.requireKey("aktørId") }
            validate { it.requireKey("vedtaksperiodeId") }
            validate { it.requireAny("tilstand", interessanteTilstander.toList()) }
            validate { it.requireKey("antallGangerPåminnet") }
        }.register(Påminnelser(slackClient, slackThreadDao, spurteDuClient))
    }

    private class Påminnelser(private val slackClient: SlackClient?, private val slackThreadDao: SlackThreadDao?, private val spurteDuClient: SpurteDuClient): River.PacketListener {
        override fun onPacket(packet: JsonMessage, context: MessageContext) {
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
                    spurteDuClient.utveksleUrl("https://spanner.intern.nav.no/person/${packet["aktørId"]}", påkrevdTilgang = tbdgruppeProd)
                )
            )
        }
        private companion object {
            private const val tbdgruppeProd = "f787f900-6697-440d-a086-d5bb56e26a9c"
        }
    }
}
