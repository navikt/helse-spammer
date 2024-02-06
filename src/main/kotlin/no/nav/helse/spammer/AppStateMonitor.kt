package no.nav.helse.spammer

import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.rapids_rivers.*
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit

internal class AppStateMonitor(
    rapidsConnection: RapidsConnection,
    private val slackClient: SlackClient?
) : River.PacketListener {

    private companion object {
        private val log = LoggerFactory.getLogger(AppStateMonitor::class.java)
        private val sikkerLogg = LoggerFactory.getLogger("tjenestekall")
        private val natt = LocalTime.MIDNIGHT..LocalTime.of(5, 0)
        // liste over apper man vil unngå å lage slackmeldinger for.
        // følger formen: <appnavn>-<kluster>-<namespace>
        private val ignorerApper = setOf("<appnavn>-prod-gcp-tbd")
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
        if (now.toLocalTime() in natt || lastReportTime > now.minusMinutes(15)) return // don't create alerts too eagerly
        val appsDown = packet["states"]
            .filterNot { it["app"].asText() in ignorerApper }
            .filter { it["state"].asInt() == 0 }
            .filter { it["last_active_time"].asLocalDateTime() < now.minusMinutes(2) }
            .map { Triple(it["app"].asText(), it["last_active_time"].asLocalDateTime(), it["instances"]
                .filter { instance -> instance.path("state").asInt() == 0 }
                .map { instance ->  Pair(instance.path("instance").asText(), instance.path("last_active_time").asLocalDateTime()) }
            ) }
        val slowInstances = packet["states"]
            .filterNot { it["app"].asText() in ignorerApper }
            .filter { it["state"].asInt() == 1 } // appsDown inneholder allerede apper som er nede;
                                                 // her måler vi heller apper som totalt sett regnes for å være oppe, men har treige instanser
            .flatMap {
                it["instances"]
                    .filter { instance -> instance.path("state").asInt() == 0 }
                    .filter { it["last_active_time"].asLocalDateTime() < now.minusSeconds(70) }
                    .filter { instance ->  instance.path("last_active_time").asLocalDateTime() > now.minusMinutes(20) }
                    .map { instance ->  Pair(instance.path("instance").asText(), instance.path("last_active_time").asLocalDateTime()) }
            }

        if (appsDown.isEmpty() && slowInstances.isEmpty()) return

        if (appsDown.isNotEmpty()) {
            val logtext = if (appsDown.size == 1) {
                val (app, sistAktivitet, _) = appsDown.first()
                val tid = humanReadableTime(ChronoUnit.SECONDS.between(sistAktivitet, now))
                String.format(
                    "*%s* er antatt nede (siste aktivitet: %s) fordi den ikke svarer tilfredsstillende på ping. Trøblete instanser i :thread:\n" +
                            ":question: Hva betyr dette for meg? Det kan bety at appen ikke leser fra Kafka, og kan ha alvorlig feil. Det kan også bety at appen har blitt drept (enten av Noen :tm: eller av :k8s:)",
                    app, tid)
            } else {
                String.format(
                    "%d apper er antatt nede da de ikke svarer tilfredsstillende på ping. Trøblete instanser i :thread:\n%s\n" +
                            ":question: Hva betyr dette for meg? Det kan bety at appen ikke leser fra Kafka, og kan ha alvorlig feil. Det kan også bety at appen har blitt drept (enten av Noen :tm: eller av :k8s:)",
                    appsDown.size,
                    appsDown.joinToString(separator = "\n") { (app, sistAktivitet, _) ->
                        val tid = humanReadableTime(ChronoUnit.SECONDS.between(sistAktivitet, now))
                        "- *$app* (siste aktivitet: $tid - $sistAktivitet)"
                    })
            }
            log.warn(logtext)
            val threadTs = slackClient?.postMessage(logtext)
            appsDown.forEach { (_, _, instances) ->
                val text = instances.joinToString(separator = "\n") { (instans, sistAktivitet) ->
                    val tid = humanReadableTime(ChronoUnit.SECONDS.between(sistAktivitet, now))
                    "- *$instans* (siste aktivitet: $tid - $sistAktivitet)"
                }
                slackClient?.postMessage(text, threadTs)
            }
        }

        if (slowInstances.isNotEmpty()) {
            val logtext = String.format(
                "%d instanser(er) er antatt nede (eller har betydelig lag) da de(n) ikke svarer tilfredsstillende på ping.\n%s\n" +
                        ":question: Hva betyr dette for meg? Det kan bety at en pod sliter med å lese en bestemt partisjon, eller at en pod har problemer/er død.",
                slowInstances.size,
                slowInstances.joinToString(separator = "\n") { (instans, sistAktivitet) ->
                    val tid = humanReadableTime(ChronoUnit.SECONDS.between(sistAktivitet, now))
                    "- *$instans* (siste aktivitet: $tid - $sistAktivitet)"
                })
            log.info(logtext)
            slackClient?.postMessage(logtext)
        }
        lastReportTime = now
    }

    private fun Triple<String, LocalDateTime, List<Pair<String, LocalDateTime>>>.printApp(): String {
        val tid = humanReadableTime(ChronoUnit.SECONDS.between(second, LocalDateTime.now()))
        return "$first (siste aktivitet: $tid)"
    }
}
