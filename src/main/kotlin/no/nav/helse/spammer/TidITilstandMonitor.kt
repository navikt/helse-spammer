package no.nav.helse.spammer

import com.fasterxml.jackson.databind.JsonNode
import net.logstash.logback.argument.StructuredArguments.keyValue
import no.nav.helse.rapids_rivers.*
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME
import java.time.temporal.ChronoUnit
import kotlin.math.absoluteValue
import kotlin.time.ExperimentalTime

@ExperimentalTime
internal class TidITilstandMonitor(
    rapidsConnection: RapidsConnection,
    private val slackClient: SlackClient?,
    private val slackThreadDao: SlackThreadDao?
) : River.PacketListener {

    private companion object {
        private val log = LoggerFactory.getLogger(TidITilstandMonitor::class.java)
        private val sikkerLog = LoggerFactory.getLogger("tjenestekall")
    }

    init {
        River(rapidsConnection).apply {
            validate {
                it.demandValue("@event_name", "vedtaksperiode_tid_i_tilstand")
                it.requireKey("vedtaksperiodeId", "tilstand", "nyTilstand",
                    "timeout_første_påminnelse", "tid_i_tilstand", "endret_tilstand_på_grunn_av.event_name")
                it.require("starttid", JsonNode::asLocalDateTime)
                it.require("sluttid", JsonNode::asLocalDateTime)
                it.require("makstid", JsonNode::asLocalDateTime)
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val tidITilstand = TidITilstand(packet)


        val tidITilstandTekst = humanReadableTime(tidITilstand.forventetTidITilstand.absoluteValue)
        val makstidtekst = if (tidITilstand.forventetTidITilstand < 0) "Makstid passert med $tidITilstandTekst" else "Forventet tid i tilstand var maks $tidITilstandTekst"

        log.info(
            "{} kom seg omsider videre fra {} til {} etter {} fra {} på grunn av mottatt {}. Forventet makstid i tilstand var {}. {}",
            keyValue("vedtaksperiodeId", tidITilstand.vedtaksperiodeId),
            keyValue("tilstand", tidITilstand.tilstand),
            keyValue("nyTilstand", tidITilstand.nyTilstand),
            humanReadableTime(tidITilstand.tidITilstand),
            tidITilstand.starttid.format(ISO_LOCAL_DATE_TIME),
            packet["endret_tilstand_på_grunn_av.event_name"].asText(),
            tidITilstand.makstid.format(ISO_LOCAL_DATE_TIME),
            makstidtekst
        )

        if (tidITilstand.tidITilstand < tidITilstand.forventetTidITilstand) return
        if (tidITilstand.tilstand !in listOf("AVVENTER_HISTORIKK", "AVVENTER_SIMULERING")) return
        if (slackThreadDao == null) return

        slackClient.postMessage(
            slackThreadDao, tidITilstand.vedtaksperiodeId, String.format(
                "Vedtaksperiode <%s|%s> (<%s|tjenestekall>) kom seg videre fra %s til %s etter %s siden %s (på grunn av mottatt %s). Forventet makstid i tilstand var %s. %s",
                Kibana.createUrl(
                    String.format("\"%s\"", tidITilstand.vedtaksperiodeId),
                    tidITilstand.starttid
                ),
                tidITilstand.vedtaksperiodeId,
                Kibana.createUrl(
                    String.format("\"%s\"", tidITilstand.vedtaksperiodeId),
                    tidITilstand.starttid,
                    null,
                    "tjenestekall-*"
                ),
                tidITilstand.tilstand,
                tidITilstand.nyTilstand,
                humanReadableTime(tidITilstand.tidITilstand),
                tidITilstand.starttid.format(ISO_LOCAL_DATE_TIME),
                packet["endret_tilstand_på_grunn_av.event_name"].asText(),
                tidITilstand.makstid.format(ISO_LOCAL_DATE_TIME),
                makstidtekst
            )
        )
    }

    override fun onError(problems: MessageProblems, context: MessageContext) {
        sikkerLog.error("forstod ikke vedtaksperiode_tid_i_tilstand:\n${problems.toExtendedReport()}")
    }

    private class TidITilstand(private val packet: JsonMessage) {
        val vedtaksperiodeId: String get() = packet["vedtaksperiodeId"].asText()
        val tilstand: String get() = packet["tilstand"].asText()
        val nyTilstand: String get() = packet["nyTilstand"].asText()
        val starttid: LocalDateTime get() = packet["starttid"].asLocalDateTime()
        val sluttid: LocalDateTime get() = packet["sluttid"].asLocalDateTime()
        val tidITilstand: Long get() = packet["tid_i_tilstand"].asLong()
        val makstid: LocalDateTime get() = packet["makstid"].asLocalDateTime()
        val forventetTidITilstand: Long get() = makstid
            .takeUnless { it == LocalDateTime.MAX }
            ?.let { ChronoUnit.SECONDS.between(starttid, it) }
            ?: Long.MAX_VALUE
    }
}
