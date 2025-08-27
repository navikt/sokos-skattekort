package no.nav.sokos.skattekort.config

import com.zaxxer.hikari.HikariDataSource
import io.kotest.core.listeners.TestListener
import io.kotest.core.spec.Spec
import io.kotest.extensions.testcontainers.toDataSource
import org.flywaydb.core.Flyway
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName

object DbListener : TestListener {
    override suspend fun beforeSpec(spec: Spec) {
        super.beforeSpec(spec)

        Flyway
            .configure()
            .dataSource(dataSource)
            .initSql("""SET ROLE "test-admin"""")
            .lockRetryCount(-1)
            .validateMigrationNaming(true)
            .load()
            .migrate()
            .migrationsExecuted
    }

    override suspend fun afterSpec(spec: Spec) {
    }

    val container =
        PostgreSQLContainer<Nothing>(DockerImageName.parse("postgres:latest")).apply {
            withReuse(true)
            withUsername("test-admin")
            waitingFor(Wait.defaultWaitStrategy())
            start()
        }
    val dataSource: HikariDataSource by lazy { container.toDataSource() }

    init {
    }
}
