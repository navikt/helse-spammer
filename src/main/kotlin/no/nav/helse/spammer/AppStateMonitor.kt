package no.nav.helse.spammer

import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.rapids_rivers.*
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

internal class AppStateMonitor(
    rapidsConnection: RapidsConnection,
    private val slackClient: SlackClient?
) : River.PacketListener {

    private companion object {
        private val log = LoggerFactory.getLogger(AppStateMonitor::class.java)
        private val sikkerLogg = LoggerFactory.getLogger("tjenestekall")
    }

    init {
        River(rapidsConnection).apply {
            validate { it.demandValue("@event_name", "app_status") }
            validate { it.requireArray("states") {
                requireKey("app", "state")
                require("last_active_time", JsonNode::asLocalDateTime)
                requireArray("instances") {
                    requireKey("instance", "state")
                    require("last_active_time", JsonNode::asLocalDateTime)
                }
            } }
            validate { it.require("threshold", JsonNode::asLocalDateTime) }
            validate { it.require("@opprettet", JsonNode::asLocalDateTime) }
        }.register(this)
    }

    private var lastReportTime = LocalDateTime.MIN
    override fun onError(problems: MessageProblems, context: MessageContext) {
        log.error(problems.toString())
        sikkerLogg.error(problems.toExtendedReport())
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val now = LocalDateTime.now()
        if (lastReportTime > now.minusMinutes(15)) return // don't create alerts too eagerly
        val appsDown = packet["states"]
            .filter { it["state"].asInt() == 0 }
            .filter { it["last_active_time"].asLocalDateTime() < now.minusMinutes(2) }
            .map { Triple(it["app"].asText(), it["last_active_time"].asLocalDateTime(), it["instances"]
                .filter { instance -> instance.path("state").asInt() == 0 }
                .map { instance ->  Pair(instance.path("instance").asText(), instance.path("last_active_time").asLocalDateTime()) }
            ) }

        if (appsDown.isEmpty()) return

        val logtext = String.format("%d app(er) er antatt nede da de(n) ikke svarer tilfredsstillende på ping. Trøblete instanser i :thread:\n%s", appsDown.size, appsDown.joinToString { (app, sistAktivitet, _) ->
            val tid = humanReadableTime(ChronoUnit.SECONDS.between(sistAktivitet, now))
            "$app (siste aktivitet: $tid - $sistAktivitet)"
        })
        log.warn(logtext)
        val threadTs = slackClient?.postMessage(logtext)
        appsDown.forEach { (_, _, instances) ->
            val text = instances.joinToString(separator = "\n") { (instans, sistAktivitet) ->
                val tid = humanReadableTime(ChronoUnit.SECONDS.between(sistAktivitet, now))
                "- $instans (siste aktivitet: $tid - $sistAktivitet)\n"
            }
            slackClient?.postMessage(text, threadTs)
        }
        lastReportTime = now
    }

    private fun Triple<String, LocalDateTime, List<Pair<String, LocalDateTime>>>.printApp(): String {
        val tid = humanReadableTime(ChronoUnit.SECONDS.between(second, LocalDateTime.now()))
        return "$first (siste aktivitet: $tid)"
    }
}
