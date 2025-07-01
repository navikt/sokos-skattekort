package no.nav.sokos.lavendel.config

import javax.sql.DataSource

import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

class DatabaseMigrator(
    dataSource: DataSource,
    adminRole: String,
) {
    init {
        org.flywaydb.core.Flyway
            .configure()
            .dataSource(dataSource)
            .initSql("""SET ROLE "$adminRole"""")
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
