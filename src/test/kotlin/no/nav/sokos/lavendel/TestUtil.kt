package no.nav.sokos.lavendel

import java.sql.Connection.TRANSACTION_SERIALIZABLE
import java.sql.ResultSet
import javax.sql.DataSource

import io.ktor.server.config.MapApplicationConfig
import org.apache.activemq.artemis.core.server.embedded.EmbeddedActiveMQ
import org.testcontainers.containers.PostgreSQLContainer

internal const val API_BASE_PATH = "/api/v1"

object TestUtil {
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

        val sql = TestUtil.readFile(fileToLoad)
        val connection = dataSource.connection
        // TODO: close connection
        connection.transactionIsolation = TRANSACTION_SERIALIZABLE
        connection.autoCommit = false
        connection.prepareStatement(sql).execute()
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
                        // Aldri plasser tabeller i public. Kommuniser hva slags funksjon tabellene dine holder til i. tabellnavn = domenebegrep, skjema = funksjon
                        val tableName = resultSet.getString("TABLE_NAME")
                        if (tableName.uppercase() != "FLYWAY_SCHEMA_HISTORY") {
                            results.add(schema + "." + tableName)
                        }
                    }
                    results
                }
        connection.prepareStatement("SET CONSTRAINTS ALL DEFERRED").execute()
        tables.asReversed().forEach { table ->
            connection.prepareStatement("DELETE FROM $table").execute()
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

    fun getOverrides(
        mQ: EmbeddedActiveMQ,
        queueName: String,
    ): MapApplicationConfig {
        MapApplicationConfig().apply {
            put("MQ_HOSTNAME", mQ.activeMQServer.configuration.addressSettings)
            put("MQ_PORT")
            put("MQ_CHANNEL_NAME")
            put("MQ_QUEUE_MANAGER_NAME")
            put("MQ_USERAUTH")
            put("MQ_BEST_QUEUE", queueName)
        }
    }
}
