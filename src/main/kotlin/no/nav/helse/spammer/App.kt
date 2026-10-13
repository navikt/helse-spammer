package no.nav.helse.spammer

import com.github.navikt.tbd_libs.personpseudoid.ValkeyPersonPseudoIdProvider
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import no.nav.helse.rapids_rivers.RapidApplication
import kotlin.time.ExperimentalTime

@ExperimentalTime
fun main() {
    val env = System.getenv()
    val dataSourceBuilder = env["DATABASE_HOST"]?.let { DataSourceBuilder(env) }

    val slackOpsClient =
        env["SLACK_BOT_USER_OAUTH_TOKEN"]?.let {
            SlackClient(
                accessToken = it,
                channel = env.getOrElse("SLACK_CHANNEL_ID_OPS") { env.getValue("SLACK_CHANNEL_ID_ALERTS") },
            )
        }
    val slackAlertsClient =
        env["SLACK_BOT_USER_OAUTH_TOKEN"]?.let {
            SlackClient(
                accessToken = it,
                channel = env.getValue("SLACK_CHANNEL_ID_ALERTS"),
            )
        }

    val slackThreadDao = dataSourceBuilder?.let { SlackThreadDao(dataSourceBuilder.getDataSource()) }

    val personPseudoIdProvider = ValkeyPersonPseudoIdProvider.fraEnv(instansNavn = "personpseudoid", env = env)

    RapidApplication
        .create(env)
        .apply {
            UtbetalingMonitor(this, slackAlertsClient, slackThreadDao)
            PåminnelseMonitor(this, slackAlertsClient, slackThreadDao, personPseudoIdProvider)
            AvstemmingMonitor(this, slackOpsClient)
            AppStateMonitor(this, slackAlertsClient)
            LoopMonitor(this, slackAlertsClient, personPseudoIdProvider)
            SlackmeldingMonitor(this, slackOpsClient, slackAlertsClient)
        }.apply {
            register(
                object : RapidsConnection.StatusListener {
                    override fun onStartup(rapidsConnection: RapidsConnection) {
                        dataSourceBuilder?.migrate()
                    }
                },
            )
        }.start()
}
