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

// TODO: Bør kanskje renames til noe slikt som databaseadmin eller noe? Det er i
// kke konfigurasjonen som tilbys, først og fremst, det er vedlikehold av skjemaet?
class DatabaseConfig(
    private val dataSource: DataSource,
) {
    val dataSourceScheduler: DataSource by lazy {
        HikariDataSource(
            initHikariConfig(
                poolname = "postgres-scheduler-pool",
            ).apply {
                maximumPoolSize = 30
                minimumIdle = 5
            },
        )
    }

    init {
        if (!(PropertiesConfig.isLocal || PropertiesConfig.isTest)) {
            Runtime.getRuntime().addShutdownHook(
                Thread {
                    (dataSource as HikariDataSource).close()
                },
            )
        }
    }

    fun migrate(dataSource: DataSource = this.dataSource) {
        val flyway =
            Flyway
                .configure()
                .dataSource(dataSource)
                .lockRetryCount(-1)
                .validateMigrationNaming(true)
                .sqlMigrationSeparator("__")
                .sqlMigrationPrefix("V")
                .load()

        val pending = flyway.info().pending().isNotEmpty()
        if (!pending) {
            logger.info { "Flyway: no pending migrations, skipping migrate()" }
            return
        }

        val result = flyway.migrate()
        logger.info { "Flyway migrate finished. executed=${result.migrationsExecuted}" }
    }

    fun initHikariConfig(poolname: String = "postgres-pool"): HikariConfig {
        val postgresProperties: PropertiesConfig.PostgresProperties = PropertiesConfig.postgresProperties
        return HikariConfig().apply {
            poolName = poolname
            maximumPoolSize = 15
            minimumIdle = 1
            isAutoCommit = false
            connectionTimeout = Duration.ofSeconds(30).toMillis()
            initializationFailTimeout = Duration.ofMinutes(10).toMillis()
            idleTimeout = Duration.ofMinutes(10).toMillis()
            maxLifetime = Duration.ofMinutes(30).toMillis()

            when {
                !(PropertiesConfig.isLocal || PropertiesConfig.isTest) -> {
                    jdbcUrl = postgresProperties.jdbcUrl
                    logger.info { "Setting up PostgreSQL" }
                }

                else -> {
                    logger.info { "Setting up local PostgreSQL" }
                    this.dataSource =
                        PGSimpleDataSource().apply {
                            user = postgresProperties.username
                            password = postgresProperties.password
                            serverNames = arrayOf(postgresProperties.host)
                            databaseName = postgresProperties.name
                            portNumbers = intArrayOf(postgresProperties.port.toInt())
                        }
                }
            }
            metricsTrackerFactory = MicrometerMetricsTrackerFactory(prometheusMeterRegistry)
        }
    }
}
