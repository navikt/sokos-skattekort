package no.nav.sokos.lavendel.config

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.postgresql.ds.PGSimpleDataSource

import no.nav.vault.jdbc.hikaricp.HikariCPVaultUtil

object DatabaseConfig {
    private lateinit var applicationProperties: PropertiesConfig.ApplicationProperties
    private lateinit var postgresProperties: PropertiesConfig.PostgresProperties
    private lateinit var dataSourcePriv: HikariDataSource
    private lateinit var adminDataSourcePriv: HikariDataSource

    val dataSource: HikariDataSource by lazy {
        dataSourcePriv
    }
    val adminDataSource: HikariDataSource by lazy {
        adminDataSourcePriv
    }

    fun init(
        config: PropertiesConfig.Configuration,
        isLocal: Boolean,
    ) {
        this.applicationProperties = config.applicationProperties
        this.postgresProperties = config.postgresProperties
        try {
            check(::postgresProperties.isInitialized) { "PostgresProperties not initialized" }
            check(::applicationProperties.isInitialized) { "ApplicationProperties not initialized" }
            when (isLocal) {
                true -> {
                    dataSourcePriv = HikariDataSource(hikariConfig(true))
                    adminDataSourcePriv = HikariDataSource(hikariConfig(true))
                }

                else -> {
                    dataSourcePriv = createPostgresDataSource(hikariConfig(false), postgresProperties.userRole)
                    adminDataSourcePriv = createPostgresDataSource(hikariConfig(false), postgresProperties.adminRole)
                }
            }
        } catch (e: Exception) {
            throw e
        }
    }

    private fun createPostgresDataSource(
        hikariConfig: HikariConfig,
        role: String,
    ): HikariDataSource =
        HikariCPVaultUtil.createHikariDataSourceWithVaultIntegration(
            hikariConfig,
            postgresProperties.vaultMountPath,
            role,
        )

    private fun hikariConfig(isLocal: Boolean): HikariConfig =
        HikariConfig().apply {
            maximumPoolSize = 5
            minimumIdle = 1
            isAutoCommit = false
            transactionIsolation = "TRANSACTION_SERIALIZABLE"
            dataSource =
                PGSimpleDataSource().apply {
                    if (isLocal) {
                        user = postgresProperties.adminUsername
                        password = postgresProperties.adminPassword
                    }
                    serverNames = arrayOf(postgresProperties.host)
                    databaseName = postgresProperties.name
                    portNumbers = intArrayOf(postgresProperties.port.toInt())
                }
        }
}
