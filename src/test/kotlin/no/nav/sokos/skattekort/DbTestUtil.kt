package no.nav.sokos.skattekort

import java.sql.Connection.TRANSACTION_SERIALIZABLE
import java.sql.ResultSet
import javax.sql.DataSource

import io.ktor.server.config.MapApplicationConfig
import kotliquery.Row
import kotliquery.queryOf
import kotliquery.sessionOf
import org.testcontainers.containers.PostgreSQLContainer

import no.nav.sokos.skattekort.bestilling.Bestilling
import no.nav.sokos.skattekort.config.DbListener
import no.nav.sokos.skattekort.person.PersonId
import no.nav.sokos.skattekort.util.SQLUtils.transaction

internal const val API_BASE_PATH = "/api/v1"

object DbTestUtil {
    fun readFile(fileName: String): String =
        this::class.java.classLoader
            .getResourceAsStream(fileName)
            ?.bufferedReader()
            ?.readLines()
            ?.joinToString(separator = "\n")!!

    fun loadDataSet(
        fileToLoad: String,
        dataSource: DataSource,
    ) {
        deleteAllTables(dataSource) // Vi vil alltid helst starte med en kjent databasetilstand.

        val sql = readFile(fileToLoad)
        val connection = dataSource.connection
        connection.transactionIsolation = TRANSACTION_SERIALIZABLE
        connection.autoCommit = false
        connection.prepareStatement(sql).execute()
        connection.commit()
        connection.close()
        updateIdentitySequences(dataSource)
    }

    fun updateIdentitySequences(dataSource: DataSource) {
        val connection = dataSource.connection

        connection.autoCommit = false
        connection.transactionIsolation = TRANSACTION_SERIALIZABLE

        val metadata = connection.metaData

        val tables =
            metadata
                .getTables(null, null, null, arrayOf<String>("TABLE"))
                .use<ResultSet, List<String>> { resultSet ->
                    val results = mutableListOf<String>()
                    while (resultSet.next()) {
                        val schema = resultSet.getString("TABLE_SCHEM") // Med takk til Sun for ubrukelig tabellnavn
                        // Aldri plasser tabeller i public. Kommuniser hva slags funksjon tabellene dine holder til i. tabellnavn = domenebegrep, skjema = funksjon
                        val tableName = resultSet.getString("TABLE_NAME")
                        if (tableName.uppercase() != "FLYWAY_SCHEMA_HISTORY") {
                            results.add(schema + "." + tableName)
                        }
                    }
                    results
                }
        val tablesWithId =
            tables.mapNotNull { schemaTable ->
                val (schema, table) = schemaTable.split(".")
                val resultSet = metadata.getColumns(null, schema, table, "id")
                if (resultSet.next()) {
                    schemaTable
                } else {
                    null // No id column
                }
            }
        tablesWithId.asReversed().forEach { table ->
            connection
                .prepareStatement(
                    "SELECT setval(pg_get_serial_sequence('$table', 'id'), " +
                        "COALESCE((SELECT MAX(id) FROM $table), 0) + 1, " +
                        "false);",
                ).execute()
        }
        connection.commit()
        connection.close()
    }

    fun deleteAllTables(dataSource: DataSource) {
        val connection = dataSource.connection

        connection.autoCommit = false
        connection.transactionIsolation = TRANSACTION_SERIALIZABLE

        val metadata = connection.metaData

        val tables =
            metadata
                .getTables(null, null, null, arrayOf<String>("TABLE"))
                .use<ResultSet, List<String>> { resultSet ->
                    val results = mutableListOf<String>()
                    while (resultSet.next()) {
                        val schema = resultSet.getString("TABLE_SCHEM") // Med takk til Sun for ubrukelig tabellnavn

                        val tableName = resultSet.getString("TABLE_NAME")
                        if (tableName.uppercase() != "FLYWAY_SCHEMA_HISTORY") {
                            results.add(schema + "." + tableName)
                        }
                    }
                    results
                }

        connection.prepareStatement("SET CONSTRAINTS ALL DEFERRED").execute()
        tables.asReversed().forEach { table ->
            connection.prepareStatement("TRUNCATE $table RESTART IDENTITY CASCADE").execute()
        }
        connection.commit()
        connection.close()
    }

    fun getOverrides(container: PostgreSQLContainer<Nothing>): MapApplicationConfig =
        MapApplicationConfig().apply {
            put("POSTGRES_USER_USERNAME", container.username)
            put("POSTGRES_USER_PASSWORD", container.password)
            put("POSTGRES_ADMIN_USERNAME", container.username)
            put("POSTGRES_ADMIN_PASSWORD", container.password)
            put("POSTGRES_NAME", container.databaseName)
            put("POSTGRES_PORT", container.firstMappedPort.toString())
            put("POSTGRES_HOST", container.host)
            put("USE_AUTHENTICATION", "false")
            put("APPLICATION_PROFILE", "LOCAL")
        }

    fun storedBestillings(
        dataSource: DataSource,
        whereClause: String?,
    ): List<Bestilling> =
        sessionOf(dataSource).use {
            it.transaction {
                it.run(
                    queryOf("SELECT person_id, fnr, aar FROM bestillinger WHERE " + (whereClause ?: "1=1"))
                        .map { row -> Bestilling(person_id = PersonId(row.long("person_id")), bestiller = "null", inntektYear = row.string("aar"), fnr = row.string("fnr")) }
                        .asList,
                )
            }
        }

    fun readFromBestillings(): List<Bestilling> =
        DbListener.dataSource.transaction { session ->
            session.list(
                queryOf("SELECT aar, fnr FROM bestillinger"),
                { row: Row ->
                    Bestilling(PersonId(1234), "OS", row.string("aar"), row.string("fnr"))
                },
            )
        }
}
