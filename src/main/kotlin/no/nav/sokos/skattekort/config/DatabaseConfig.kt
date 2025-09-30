package no.nav.sokos.skattekort.config

import java.time.Duration

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import com.zaxxer.hikari.metrics.micrometer.MicrometerMetricsTrackerFactory
import mu.KotlinLogging
import org.flywaydb.core.Flyway
import org.postgresql.ds.PGSimpleDataSource

import no.nav.sokos.skattekort.metrics.Metrics.prometheusMeterRegistry
import no.nav.vault.jdbc.hikaricp.HikariCPVaultUtil

private val logger = KotlinLogging.logger {}

object DatabaseConfig {
    val dataSource: HikariDataSource by lazy {
        initDataSource()
    }

    init {
        Runtime.getRuntime().addShutdownHook(
            Thread {
                dataSource.close()
            },
        )
    }

    fun migrate(
        dataSource: HikariDataSource =
            initDataSource(
                hikariConfig = initHikariConfig("postgres-admin-pool"),
                role = PropertiesConfig.getPostgresProperties().adminRole,
            ),
    ) {
        dataSource.use { connection ->
            Flyway
                .configure()
                .dataSource(connection)
                .initSql("""SET ROLE "${PropertiesConfig.getPostgresProperties().adminRole}"""")
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
            maximumPoolSize = 5
            minimumIdle = 1
            isAutoCommit = false
            transactionIsolation = "TRANSACTION_SERIALIZABLE"
            this.dataSource =
                PGSimpleDataSource().apply {
                    if (PropertiesConfig.isLocal() || PropertiesConfig.isTest()) {
                        user = postgresProperties.adminUsername
                        password = postgresProperties.adminPassword
                    }
                    serverNames = arrayOf(postgresProperties.host)
                    databaseName = postgresProperties.name
                    portNumbers = intArrayOf(postgresProperties.port.toInt())
                    connectionTimeout = Duration.ofSeconds(10).toMillis()
                    initializationFailTimeout = Duration.ofMinutes(5).toMillis()
                }
            metricsTrackerFactory = MicrometerMetricsTrackerFactory(prometheusMeterRegistry)
        }
    }

    private fun initDataSource(
        hikariConfig: HikariConfig = initHikariConfig(),
        role: String = PropertiesConfig.getPostgresProperties().userRole,
    ): HikariDataSource =
        when {
            PropertiesConfig.isLocal() || PropertiesConfig.isTest() -> HikariDataSource(hikariConfig)
            else ->
                HikariCPVaultUtil.createHikariDataSourceWithVaultIntegration(
                    hikariConfig,
                    PropertiesConfig.getPostgresProperties().vaultMountPath,
                    role,
                )
        }
}
