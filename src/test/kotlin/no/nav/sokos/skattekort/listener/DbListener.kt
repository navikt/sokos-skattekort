package no.nav.sokos.skattekort.listener

import com.zaxxer.hikari.HikariDataSource
import io.kotest.core.listeners.AfterSpecListener
import io.kotest.core.listeners.BeforeSpecListener
import io.kotest.core.spec.Spec
import io.kotest.extensions.testcontainers.toDataSource
import mu.KotlinLogging
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.containers.wait.strategy.Wait

import no.nav.sokos.skattekort.config.DatabaseConfig

private val logger = KotlinLogging.logger {}

object DbListener : BeforeSpecListener, AfterSpecListener {
    val container: PostgreSQLContainer<Nothing> =
        PostgreSQLContainer<Nothing>("postgres:latest").apply {
            withReuse(false)
            withUsername("test-admin")
            withPassword("test-password")
            withDatabaseName("test")
            waitingFor(Wait.defaultWaitStrategy())
            start()
        }

    val dataSource: HikariDataSource by lazy {
        container.toDataSource()
    }.apply {
        // Gotcha: Kan ikke use this.dataSource fordi migrate vil stenge databasepoolen. Vi har to pooler i prod - en admin og en for vanlig bruk,
        // og migrate sørger for å gjøre admin-poolen utilgjengelig. Derfor lager vi en ny databasepool her, for å herme den oppførselen.
        DatabaseConfig.migrate(container.toDataSource(), "test-admin")
    }

    override suspend fun beforeSpec(spec: Spec) {
    }
}
