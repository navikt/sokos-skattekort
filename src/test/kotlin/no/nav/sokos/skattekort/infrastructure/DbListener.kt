package no.nav.sokos.skattekort.infrastructure

import javax.sql.DataSource

import io.kotest.core.listeners.AfterEachListener
import io.kotest.core.listeners.BeforeSpecListener
import io.kotest.core.test.TestCase
import io.kotest.engine.test.TestResult
import io.kotest.extensions.testcontainers.toDataSource
import kotliquery.queryOf
import mu.KotlinLogging
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.ext.ScriptUtils
import org.testcontainers.jdbc.JdbcDatabaseDelegate

import no.nav.sokos.skattekort.config.DatabaseConfig
import no.nav.sokos.skattekort.util.SQLUtils.transaction

private val logger = KotlinLogging.logger {}

object DbListener : BeforeSpecListener, AfterEachListener {
    val container: PostgreSQLContainer<Nothing> =
        PostgreSQLContainer<Nothing>("postgres:latest").apply {
            withReuse(false)
            withUsername("test-admin")
            withPassword("test-password")
            withDatabaseName("test")
            waitingFor(Wait.defaultWaitStrategy())
            start()
        }

    val dataSource: DataSource by lazy {
        container.toDataSource()
    }.apply {
        DatabaseConfig.migrate(container.toDataSource(), "test-admin")
    }

    fun loadDataSet(script: String) {
        if (script.isNotEmpty()) {
            ScriptUtils.runInitScript(JdbcDatabaseDelegate(container, ""), script)
        }
    }

    override suspend fun afterEach(
        testCase: TestCase,
        result: TestResult,
    ) {
        dataSource.transaction { session ->
            val tables = mutableListOf<String>()
            // Collect all public tables except Flyway history
            session.list(
                queryOf("SELECT tablename FROM pg_tables WHERE schemaname='public' AND tablename <> 'flyway_schema_history'"),
            ) { rs -> tables += rs.string("tablename") }

            if (tables.isNotEmpty()) {
                session.execute(queryOf("TRUNCATE TABLE ${tables.joinToString(", ")} RESTART IDENTITY CASCADE"))
            }
        }
    }
}
