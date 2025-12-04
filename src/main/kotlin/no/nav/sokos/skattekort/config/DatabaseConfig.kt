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

    val dataSourceReadCommit: DataSource by lazy {
        HikariDataSource(
            initHikariConfig(
                poolname = "postgres-read-commited-pool",
                transactionIsolation = "TRANSACTION_READ_COMMITTED",
            ),
        )
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

    fun migrate(dataSource: DataSource = this.dataSource) {
        Flyway
            .configure()
            .dataSource(dataSource)
            .lockRetryCount(-1)
            .validateMigrationNaming(true)
            .sqlMigrationSeparator("__")
            .sqlMigrationPrefix("V")
            .load()
            .migrate()
            .migrationsExecuted
        logger.info { "Migration finished" }
    }

    private fun initHikariConfig(
        poolname: String = "postgres-pool",
        transactionIsolation: String = "TRANSACTION_SERIALIZABLE",
    ): HikariConfig {
        val postgresProperties: PropertiesConfig.PostgresProperties = PropertiesConfig.getPostgresProperties()
        return HikariConfig().apply {
            poolName = poolname
            maximumPoolSize = 10
            minimumIdle = 1
            isAutoCommit = false
            this.transactionIsolation = transactionIsolation
            connectionTimeout = Duration.ofSeconds(10).toMillis()
            initializationFailTimeout = Duration.ofMinutes(5).toMillis()

            when {
                !(PropertiesConfig.isLocal() || PropertiesConfig.isTest()) -> {
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
