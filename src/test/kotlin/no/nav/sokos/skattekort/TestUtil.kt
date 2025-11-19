package no.nav.sokos.skattekort

import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.stream.Collectors
import javax.sql.DataSource

import kotlin.time.Duration.Companion.seconds

import io.kotest.assertions.nondeterministic.eventuallyConfig
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.application.pluginOrNull
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.plugins.di.DI
import io.ktor.server.plugins.di.DefaultConflictPolicy
import io.ktor.server.plugins.di.DependencyConflictPolicy
import io.ktor.server.plugins.di.DependencyConflictResult
import io.ktor.server.plugins.di.DependencyInjectionConfig
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.mockk.mockk
import jakarta.jms.ConnectionFactory
import jakarta.jms.Queue
import kotliquery.TransactionalSession
import kotliquery.queryOf
import org.apache.activemq.artemis.jms.client.ActiveMQQueue

import no.nav.security.mock.oauth2.withMockOAuth2Server
import no.nav.sokos.skattekort.config.PropertiesConfig
import no.nav.sokos.skattekort.infrastructure.DbListener
import no.nav.sokos.skattekort.infrastructure.MQListener
import no.nav.sokos.skattekort.security.AzuredTokenClient
import no.nav.sokos.skattekort.security.MaskinportenTokenClient
import no.nav.sokos.skattekort.util.SQLUtils.transaction

object TestUtil {
    val eventuallyConfiguration =
        eventuallyConfig {
            initialDelay = 1.seconds
            retries = 3
        }

    fun readFile(filename: String): String {
        val inputStream = this::class.java.getResourceAsStream(filename)!!
        return BufferedReader(InputStreamReader(inputStream))
            .lines()
            .parallel()
            .collect(Collectors.joining("\n"))
    }

    fun withFullTestApplication(thunk: suspend ApplicationTestBuilder.() -> Unit) =
        withMockOAuth2Server {
            testApplication {
                application {
                    configureTestModule()
                }
                startApplication()
                thunk()
            }
        }

    fun Application.configureTestModule() {
        if (pluginOrNull(DI) == null) {
            install(DI) {
                configureShutdownBehavior()
            }

            dependencies {
                provide { mockk<MaskinportenTokenClient>(relaxed = true) }
                provide { mockk<AzuredTokenClient>(relaxed = true) }
                provide<ConnectionFactory> { MQListener.connectionFactory }
                provide<Queue>(name = "forespoerselQueue") {
                    ActiveMQQueue(PropertiesConfig.getMQProperties().fraForSystemQueue)
                }
                provide<Queue>(name = "leveransekoeOppdragZSkattekort") {
                    ActiveMQQueue(PropertiesConfig.getMQProperties().leveransekoeOppdragZSkattekort)
                }
            }
        }
        module(testEnvironmentConfig())
    }

    fun runThisSql(query: String) {
        DbListener.dataSource.transaction { session ->
            session.run(
                queryOf(
                    query,
                ).asExecute,
            )
        }
    }

    fun <T> tx(block: (TransactionalSession) -> T): T = DbListener.dataSource.transaction { tx -> block(tx) }

    private fun testEnvironmentConfig(): MapApplicationConfig =
        MapApplicationConfig().apply {
            put("APPLICATION_ENV", "TEST")

            // Database properties
            put("POSTGRES_USER_USERNAME", DbListener.container.username)
            put("POSTGRES_USER_PASSWORD", DbListener.container.password)
            put("POSTGRES_ADMIN_USERNAME", DbListener.container.username)
            put("POSTGRES_ADMIN_PASSWORD", DbListener.container.password)
            put("POSTGRES_NAME", DbListener.container.databaseName)
            put("POSTGRES_PORT", DbListener.container.firstMappedPort.toString())
            put("POSTGRES_HOST", DbListener.container.host)
        }

    private fun DependencyInjectionConfig.configureShutdownBehavior() {
        conflictPolicy =
            DependencyConflictPolicy { prev, current ->
                when (val result = DefaultConflictPolicy.resolve(prev, current)) {
                    is DependencyConflictResult.Conflict -> DependencyConflictResult.KeepPrevious
                    else -> result
                }
            }

        onShutdown = { dependencyKey, instance ->
            when (instance) {
                // Vi ønsker bare en DataSource i bruk for en hel test-kjøring, selv om flere tester start/stopper applikasjonen
                // dette er en opt-out av auto-close-greiene til Kotlins DI-extension:
                is MaskinportenTokenClient -> {}
                is MaskinportenTokenClient? -> {}
                is AzuredTokenClient -> {}
                is DataSource -> {}
                is ConnectionFactory -> {}
                is ConnectionFactory? -> {}
                is Queue -> {}
                is Queue? -> {}
                is AutoCloseable -> instance.close()
            }
        }
    }
}
