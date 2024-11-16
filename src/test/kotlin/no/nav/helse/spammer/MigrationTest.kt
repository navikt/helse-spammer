package no.nav.helse.spammer

import com.github.navikt.tbd_libs.test_support.DatabaseContainers
import com.github.navikt.tbd_libs.test_support.TestDataSource
import kotliquery.queryOf
import kotliquery.sessionOf
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

val databaseContainer = DatabaseContainers.container("spammer")
internal class MigrationTest {

    private lateinit var testDataSource: TestDataSource

    @BeforeEach
    fun setup() {
        testDataSource = databaseContainer.nyTilkobling()
    }

    @AfterEach
    fun cleanup() {
        databaseContainer.droppTilkobling(testDataSource)
    }

    @Test
    fun `migreringer skal kjøre`() {
        assertTrue(sessionOf(testDataSource.ds).use {
            it.run(queryOf("select exists (select from information_schema.tables where table_schema='public' and table_name=?)", "slack_thread").map { row ->
                row.boolean(1)
            }.asList).single()
        })
    }
}
