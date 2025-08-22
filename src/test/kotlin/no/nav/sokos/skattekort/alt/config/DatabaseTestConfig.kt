package no.nav.sokos.skattekort.alt.config

import java.time.Duration

import com.zaxxer.hikari.HikariConfig
import org.postgresql.ds.PGSimpleDataSource

object DatabaseTestConfig {
    fun hikariPostgresConfig(host: String) =
        HikariConfig().apply {
            poolName = "HikariPool-SOKOS-SKATTEKORT-POSTGRES"
            maximumPoolSize = 5
            minimumIdle = 1
            username = "test-admin"
            password = "postgres"
            dataSource =
                PGSimpleDataSource().apply {
                    serverNames = arrayOf(host)
                    databaseName = "DaDB"
                    portNumbers = intArrayOf(5432)
                    connectionTimeout = Duration.ofSeconds(10).toMillis()
                    maxLifetime = Duration.ofMinutes(30).toMillis()
                    initializationFailTimeout = Duration.ofMinutes(30).toMillis()
                }
        }
}
