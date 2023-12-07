package no.nav.helse.spammer

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.slf4j.LoggerFactory
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.time.LocalDateTime
internal class SpurteDuClient(
    private val host: String
) {

    private companion object {
        private val tjenestekall = LoggerFactory.getLogger("tjenestekall")
        private val log = LoggerFactory.getLogger(SlackClient::class.java)
        private val objectMapper = jacksonObjectMapper()
            .registerModule(JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    }

    fun utveksleTekst(tekst: String, påkrevdTilgang: String?): String? {
        return utveksle(mapOf(
            "tekst" to tekst,
            "påkrevdTilgang" to påkrevdTilgang
        ))
    }
    fun utveksleUrl(url: String, påkrevdTilgang: String?): String? {
        return utveksle(mapOf(
            "url" to url,
            "påkrevdTilgang" to påkrevdTilgang
        ))
    }
    private fun utveksle(data: Map<String, String?>): String? {
        return "http://spurtedu/skjul_meg".post(objectMapper.writeValueAsString(data))?.let {
            host + objectMapper.readTree(it)["path"]?.asText()
        }
    }

    private fun String.post(jsonPayload: String): String? {
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(this).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 5000
                readTimeout = 5000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("User-Agent", "navikt/spammer")

                outputStream.use { it.bufferedWriter(Charsets.UTF_8).apply { write(jsonPayload); flush() } }
            }

            val responseCode = connection.responseCode

            if (connection.responseCode !in 200..299) {
                log.warn("response from spurtedu: code=$responseCode")
                tjenestekall.warn("response from spurtedu: code=$responseCode body=${connection.errorStream.readText()}")
                return null
            }

            val responseBody = connection.inputStream.readText()
            log.debug("response from spurtedu: code=$responseCode")
            tjenestekall.debug("response from spurtedu: code=$responseCode body=$responseBody")

            return responseBody
        } catch (err: SocketTimeoutException) {
            log.warn("timeout waiting for reply", err)
        } catch (err: IOException) {
            log.error("feil ved posting til spurtedu: {}", err.message, err)
        } finally {
            connection?.disconnect()
        }

        return null
    }

    private fun InputStream.readText() = use { it.bufferedReader().readText() }
}
