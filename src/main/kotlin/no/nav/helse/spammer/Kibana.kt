package no.nav.helse.spammer

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object Kibana {
    private const val baseUrl = "https://logs.adeo.no/app/kibana"
    private const val urlFormat = "$baseUrl#/discover?_a=%s&_g=%s"
    private const val defaultIndex = "96e648c0-980a-11e9-830a-e17bbd64b4db"

    fun createUrl(
        query: String,
        startTime: LocalDateTime,
        endTime: LocalDateTime? = null,
        index: String = defaultIndex,
    ) = createUrl(query, startTime.isoLocalDateTime(), endTime?.isoLocalDateTime() ?: "now", index)

    fun createUrl(
        query: String,
        startTime: String,
        endTime: String = "now",
        index: String = defaultIndex,
    ) = String.format(urlFormat, appState(index, query), globalState(startTime, endTime))

    private fun appState(
        index: String,
        query: String,
    ) = mapOf(
        "index" to index,
        "query" to
            mapOf(
                "language" to "lucene",
                "query" to query,
            ),
    ).toRison()

    private fun globalState(
        startTime: String,
        endTime: String,
    ) = mapOf(
        "time" to
            mapOf(
                "from" to startTime,
                "mode" to "absolute",
                "to" to endTime,
            ),
    ).toRison()

    private fun LocalDateTime.isoLocalDateTime() = this.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)

    // Rison-koding (https://github.com/Nanonid/rison) av objekter med strengverdier.
    // Erstatter com.bazaarvoice.jackson:rison, som kun finnes for Jackson 2.
    private fun Map<String, Any>.toRison(): String =
        entries.joinToString(separator = ",", prefix = "(", postfix = ")") { (key, value) ->
            "${key.toRison()}:${value.toRisonValue()}"
        }

    @Suppress("UNCHECKED_CAST")
    private fun Any.toRisonValue(): String =
        when (this) {
            is String -> toRison()
            is Map<*, *> -> (this as Map<String, Any>).toRison()
            else -> throw IllegalArgumentException("Støtter ikke rison-koding av ${this::class}")
        }

    private fun String.toRison(): String =
        if (erRisonId()) {
            this
        } else {
            "'" + replace("!", "!!").replace("'", "!'") + "'"
        }

    private fun String.erRisonId() = isNotEmpty() && first().erRisonIdStart() && drop(1).all { it.erRisonIdTegn() }

    private fun Char.erRisonIdStart() = code > 127 || this in 'a'..'z' || this in 'A'..'Z' || this in "_./~"

    private fun Char.erRisonIdTegn() = erRisonIdStart() || this in '0'..'9' || this == '-'
}
