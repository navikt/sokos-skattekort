package no.nav.sokos.skattekort.listener

import com.zaxxer.hikari.HikariDataSource
import io.kotest.core.listeners.AfterSpecListener
import io.kotest.core.listeners.BeforeSpecListener
import io.kotest.core.spec.Spec
import io.kotest.extensions.testcontainers.toDataSource
import mu.KotlinLogging
import org.flywaydb.core.Flyway
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName

private val logger = KotlinLogging.logger {}

object DbListener : BeforeSpecListener, AfterSpecListener {
    val container: PostgreSQLContainer<Nothing> =
        PostgreSQLContainer<Nothing>(DockerImageName.parse("postgres:latest")).apply {
            withReuse(false)
            withUsername("test-admin")
            waitingFor(Wait.defaultWaitStrategy())
            start()
        }

    val dataSource: HikariDataSource by lazy {
        container.toDataSource()
    }.apply {
        Flyway
            .configure()
            .dataSource(this.value)
            .lockRetryCount(-1)
            .validateMigrationNaming(true)
            .sqlMigrationSeparator("__")
            .sqlMigrationPrefix("V")
            .load()
            .migrate()
            .migrationsExecuted
        logger.info { "Migration finished" }
    }

    override suspend fun beforeSpec(spec: Spec) {
    }
}
