package no.nav.helse.spammer

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

internal class KibanaTest {
    @Test
    fun `lager url med standardindeks og sluttid now`() {
        assertEquals(
            "https://logs.adeo.no/app/kibana#/discover" +
                "?_a=(index:'96e648c0-980a-11e9-830a-e17bbd64b4db',query:(language:lucene,query:'\"abc\"'))" +
                "&_g=(time:(from:'2026-10-30T10:15:30',mode:absolute,to:now))",
            Kibana.createUrl("\"abc\"", LocalDateTime.of(2026, 10, 30, 10, 15, 30)),
        )
    }

    @Test
    fun `lager url med egen indeks og sluttid`() {
        assertEquals(
            "https://logs.adeo.no/app/kibana#/discover" +
                "?_a=(index:'tjenestekall-*',query:(language:lucene,query:'\"abc\"'))" +
                "&_g=(time:(from:'2026-10-30T10:15:30',mode:absolute,to:'2026-10-31T00:00:00'))",
            Kibana.createUrl(
                "\"abc\"",
                LocalDateTime.of(2026, 10, 30, 10, 15, 30),
                LocalDateTime.of(2026, 10, 31, 0, 0, 0),
                "tjenestekall-*",
            ),
        )
    }

    @Test
    fun `escaper og quoter verdier etter rison-reglene`() {
        assertEquals(
            "https://logs.adeo.no/app/kibana#/discover" +
                "?_a=(index:ærlig_indeks.v1/~x,query:(language:lucene,query:'it!'s !!viktig'))" +
                "&_g=(time:(from:'',mode:absolute,to:now-1d))",
            Kibana.createUrl("it's !viktig", "", "now-1d", "ærlig_indeks.v1/~x"),
        )
    }
}
