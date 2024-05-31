package no.nav.helse.spammer

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.navikt.tbd_libs.azure.AzureToken
import com.github.navikt.tbd_libs.azure.AzureTokenProvider
import com.github.navikt.tbd_libs.spurtedu.SpurteDuClient
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

    val spurteDuClient = SpurteDuClient(
        objectMapper = jacksonObjectMapper().registerModule(JavaTimeModule()),
        tokenProvider = object : AzureTokenProvider {
            // trenger egentlig ikke token provider fordi vi slår aldri opp en hemmelighet
            override fun bearerToken(scope: String): AzureToken {
                TODO("Not yet implemented")
            }

            override fun onBehalfOfToken(scope: String, token: String): AzureToken {
                TODO("Not yet implemented")
            }
        }
    )

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
