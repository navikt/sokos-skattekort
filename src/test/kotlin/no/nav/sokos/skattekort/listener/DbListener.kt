package no.nav.sokos.skattekort.listener

import com.zaxxer.hikari.HikariDataSource
import io.kotest.core.listeners.AfterSpecListener
import io.kotest.core.listeners.BeforeSpecListener
import io.kotest.core.spec.Spec
import io.kotest.extensions.testcontainers.toDataSource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName

import no.nav.sokos.skattekort.config.DatabaseMigrator

object DbListener : BeforeSpecListener, AfterSpecListener {
    val container: PostgreSQLContainer<Nothing> =
        PostgreSQLContainer<Nothing>(DockerImageName.parse("postgres:latest")).apply {
            withReuse(false)
            withUsername("test-admin")
            waitingFor(Wait.defaultWaitStrategy())
        }

    val dataSource: HikariDataSource by lazy { container.toDataSource() }

    override suspend fun beforeSpec(spec: Spec) {
        super.beforeSpec(spec)

        // Starter database-kontaineren før første testklasse som trenger den kjører. For neste testklasse blir dette en no-op.
        println("bar")
        container.start()
        // I tilfelle dette er en service-test, så kjører vi Flyway. Dersom Flyway allerede er kjørt blir dette en no-op
        DatabaseMigrator(dataSource, "test-admin")
    }

    override suspend fun afterSpec(spec: Spec) {
    }
}
