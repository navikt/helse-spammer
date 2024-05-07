package no.nav.helse.spammer

import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import kotlin.time.ExperimentalTime

@ExperimentalTime
fun main() {
    val env = System.getenv()
    val dataSourceBuilder = env["DATABASE_HOST"]?.let { DataSourceBuilder(env) }

    val slackClient = env["SLACK_ACCESS_TOKEN"]?.let {
        SlackClient(
            accessToken = it,
            channel = env.getValue("SLACK_CHANNEL_ID")
        )
    }
    val slackAlertsClient = env["SLACK_ACCESS_TOKEN"]?.let {
        SlackClient(
            accessToken = it,
            channel = env.getValue("SLACK_CHANNEL_ID_ALERTS")
        )
    }

    val slackThreadDao = dataSourceBuilder?.let { SlackThreadDao(dataSourceBuilder.getDataSource()) }

    val spurteDuClient = SpurteDuClient(when (System.getenv("NAIS_CLUSTER_NAME")) {
        "prod-gcp" -> "https://spurte-du.ansatt.nav.no"
        else -> "https://spurte-du.intern.dev.nav.no"
    })

    RapidApplication.create(env).apply {
        UtbetalingMonitor(this, slackAlertsClient, slackThreadDao)
        PåminnelseMonitor(this, slackAlertsClient, slackThreadDao, spurteDuClient)
        AvstemmingMonitor(this, slackClient)
        AppStateMonitor(this, slackAlertsClient)
        LoopMonitor(this, slackAlertsClient, spurteDuClient)
        SlackmeldingMonitor(this, slackClient, slackAlertsClient)
    }.apply {
        register(object : RapidsConnection.StatusListener {
            override fun onStartup(rapidsConnection: RapidsConnection) {
                dataSourceBuilder?.migrate()
            }
        })
    }.start()
}
