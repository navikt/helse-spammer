package no.nav.helse.spammer

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.navikt.tbd_libs.azure.AzureToken
import com.github.navikt.tbd_libs.azure.AzureTokenProvider
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import com.github.navikt.tbd_libs.result_object.Result
import com.github.navikt.tbd_libs.spurtedu.SpurteDuClient
import kotlin.time.ExperimentalTime
import no.nav.helse.rapids_rivers.RapidApplication

@ExperimentalTime
fun main() {
    val env = System.getenv()
    val dataSourceBuilder = env["DATABASE_HOST"]?.let { DataSourceBuilder(env) }

    val slackOpsClient = env["SLACK_BOT_USER_OAUTH_TOKEN"]?.let {
        SlackClient(
            accessToken = it,
            channel = env.getOrElse("SLACK_CHANNEL_ID_OPS") { env.getValue("SLACK_CHANNEL_ID_ALERTS") }
        )
    }
    val slackAlertsClient = env["SLACK_BOT_USER_OAUTH_TOKEN"]?.let {
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
            override fun bearerToken(scope: String): Result<AzureToken> {
                TODO("Not yet implemented")
            }

            override fun onBehalfOfToken(scope: String, token: String): Result<AzureToken> {
                TODO("Not yet implemented")
            }
        }
    )

    RapidApplication.create(env).apply {
        UtbetalingMonitor(this, slackAlertsClient, slackThreadDao)
        PåminnelseMonitor(this, slackAlertsClient, slackThreadDao, spurteDuClient)
        AvstemmingMonitor(this, slackOpsClient)
        AppStateMonitor(this, slackAlertsClient)
        LoopMonitor(this, slackAlertsClient, spurteDuClient)
        SlackmeldingMonitor(this, slackOpsClient, slackAlertsClient)
    }.apply {
        register(object : RapidsConnection.StatusListener {
            override fun onStartup(rapidsConnection: RapidsConnection) {
                dataSourceBuilder?.migrate()
            }
        })
    }.start()
}
