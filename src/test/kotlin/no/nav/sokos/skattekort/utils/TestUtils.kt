package no.nav.sokos.skattekort.utils

import java.io.BufferedReader
import java.io.InputStreamReader
import java.sql.Connection.TRANSACTION_SERIALIZABLE
import java.util.stream.Collectors
import javax.sql.DataSource

import kotlin.time.Duration.Companion.seconds

import io.kotest.assertions.nondeterministic.eventuallyConfig
import kotliquery.TransactionalSession
import kotliquery.queryOf

import no.nav.sokos.skattekort.listener.DbListener
import no.nav.sokos.skattekort.util.SQLUtils.transaction

object TestUtils {
    val eventuallyConfiguration =
        eventuallyConfig {
            initialDelay = 1.seconds
            retries = 3
        }

    fun readFile(filename: String): String {
        val inputStream = this::class.java.getResourceAsStream(filename)!!
        return BufferedReader(InputStreamReader(inputStream))
            .lines()
            .parallel()
            .collect(Collectors.joining("\n"))
    }

    fun runThisSql(query: String) {
        DbListener.dataSource.transaction { session ->
            session.run(
                queryOf(
                    query,
                ).asExecute,
            )
        }
        updateIdentitySequences(DbListener.dataSource)
    }

    fun updateIdentitySequences(dataSource: DataSource) {
        dataSource.connection.use { connection ->
            connection.autoCommit = false
            connection.transactionIsolation = TRANSACTION_SERIALIZABLE

            val metadata = connection.metaData

            val tables =
                metadata.getTables(null, null, null, arrayOf("TABLE")).use { resultSet ->
                    buildList {
                        while (resultSet.next()) {
                            val schema = resultSet.getString("TABLE_SCHEM")
                            val tableName = resultSet.getString("TABLE_NAME")
                            if (tableName.uppercase() != "FLYWAY_SCHEMA_HISTORY" && tableName.uppercase() != "SCHEDULED_TASKS_HISTORY") {
                                add(schema to tableName)
                            }
                        }
                    }
                }

            val tablesWithId =
                tables.mapNotNull { (schema, table) ->
                    metadata.getColumns(null, schema, table, "id").use { rs ->
                        if (rs.next()) "$schema.$table" else null
                    }
                }

            tablesWithId.asReversed().forEach { table ->
                connection
                    .prepareStatement(
                        "SELECT setval(pg_get_serial_sequence('$table', 'id'), " +
                            "COALESCE((SELECT MAX(id) FROM $table), 0) + 1, false);",
                    ).use { it.execute() }
            }

            connection.commit()
        }
    }

    fun <T> tx(block: (TransactionalSession) -> T): T = DbListener.dataSource.transaction { tx -> block(tx) }
}
