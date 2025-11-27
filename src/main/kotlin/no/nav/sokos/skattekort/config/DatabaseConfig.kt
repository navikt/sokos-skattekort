package no.nav.sokos.skattekort.config

import java.time.Duration
import javax.sql.DataSource

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import com.zaxxer.hikari.metrics.micrometer.MicrometerMetricsTrackerFactory
import mu.KotlinLogging
import org.flywaydb.core.Flyway
import org.postgresql.ds.PGSimpleDataSource

import no.nav.sokos.skattekort.infrastructure.Metrics.prometheusMeterRegistry

private val logger = KotlinLogging.logger {}

object DatabaseConfig {
    val dataSource: DataSource by lazy {
        HikariDataSource(initHikariConfig())
    }

    init {
        if (!(PropertiesConfig.isLocal() || PropertiesConfig.isTest())) {
            Runtime.getRuntime().addShutdownHook(
                Thread {
                    (dataSource as HikariDataSource).close()
                },
            )
        }
    }

    fun migrate(dataSource: HikariDataSource = HikariDataSource(initHikariConfig("postgres-admin-pool"))) {
        dataSource.use { connection ->
            Flyway
                .configure()
                .dataSource(connection)
                .lockRetryCount(-1)
                .validateMigrationNaming(true)
                .sqlMigrationSeparator("__")
                .sqlMigrationPrefix("V")
                .load()
                .migrate()
                .migrationsExecuted
            logger.info { "Migration finished" }
        }
    }

    private fun initHikariConfig(poolname: String = "postgres-pool"): HikariConfig {
        val postgresProperties: PropertiesConfig.PostgresProperties = PropertiesConfig.getPostgresProperties()
        return HikariConfig().apply {
            poolName = poolname
            maximumPoolSize = 10
            minimumIdle = 1
            isAutoCommit = false
            transactionIsolation = "TRANSACTION_SERIALIZABLE"
            this.dataSource =
                PGSimpleDataSource().apply {
                    user = postgresProperties.username
                    password = postgresProperties.password
                    serverNames = arrayOf(postgresProperties.host)
                    databaseName = postgresProperties.name
                    portNumbers = intArrayOf(postgresProperties.port.toInt())
                    connectionTimeout = Duration.ofSeconds(10).toMillis()
                    initializationFailTimeout = Duration.ofMinutes(5).toMillis()

                    if (!(PropertiesConfig.isLocal() || PropertiesConfig.isTest())) {
                        sslMode = postgresProperties.sslMode
                        sslCert = postgresProperties.sslCert
                        sslKey = postgresProperties.sslKey
                        sslRootCert = postgresProperties.sslRootCert
                    }
                }
            metricsTrackerFactory = MicrometerMetricsTrackerFactory(prometheusMeterRegistry)
        }
    }
}
