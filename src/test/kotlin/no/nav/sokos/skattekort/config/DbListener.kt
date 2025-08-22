package no.nav.sokos.skattekort.config

import com.zaxxer.hikari.HikariDataSource
import io.kotest.core.listeners.TestListener
import io.kotest.core.spec.Spec
import io.kotest.extensions.testcontainers.toDataSource
import org.flywaydb.core.Flyway
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.ext.ScriptUtils
import org.testcontainers.jdbc.JdbcDatabaseDelegate
import org.testcontainers.utility.DockerImageName

object DbListener : TestListener {
    private val container =
        PostgreSQLContainer<Nothing>(DockerImageName.parse("postgres:latest")).apply {
            withReuse(false)
            withUsername("test-admin")
            waitingFor(Wait.defaultWaitStrategy())
            start()
        }

    fun loadInitScript(name: String) = ScriptUtils.runInitScript(JdbcDatabaseDelegate(container, ""), name)

    val dataSource: HikariDataSource =
        container.toDataSource {
            maximumPoolSize = 100
            minimumIdle = 1
            isAutoCommit = false
        }

    override suspend fun beforeSpec(spec: Spec) {
        container.start()
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
        container.stop()
    }
}
