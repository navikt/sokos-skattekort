package no.nav.sokos.skattekort.utils

import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.stream.Collectors
import javax.sql.DataSource

import kotlin.time.Duration.Companion.seconds

import io.kotest.assertions.nondeterministic.eventuallyConfig
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.application.pluginOrNull
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.plugins.di.DI
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

import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.security.mock.oauth2.withMockOAuth2Server
import no.nav.sokos.skattekort.config.PropertiesConfig
import no.nav.sokos.skattekort.infrastructure.DbListener
import no.nav.sokos.skattekort.infrastructure.MQListener
import no.nav.sokos.skattekort.module
import no.nav.sokos.skattekort.security.AzuredTokenClient
import no.nav.sokos.skattekort.security.JWT_CLAIM_NAVIDENT
import no.nav.sokos.skattekort.security.MaskinportenTokenClient
import no.nav.sokos.skattekort.util.SQLUtils.transaction

object TestUtils {
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

    var authServer: MockOAuth2Server? = null
    var tokenWithNavIdent: String? = null

    fun withFullTestApplication(thunk: suspend ApplicationTestBuilder.() -> Unit) =
        withMockOAuth2Server {
            authServer = this
            tokenWithNavIdent =
                this
                    .issueToken(
                        issuerId = "default",
                        claims =
                            mapOf<kotlin.String, kotlin.String>(JWT_CLAIM_NAVIDENT to "aUser"),
                    ).serialize()

            testApplication {
                application {
                    configureTestModule(authServer!!)
                }
                startApplication()

                client =
                    createClient {
                        install(ContentNegotiation) {
                            json()
                        }
                    }

                thunk()
            }
        }

    fun Application.configureTestModule(authServer: MockOAuth2Server) {
        if (pluginOrNull(DI) == null) {
            install(DI) {
                configureShutdownBehavior()
            }

            dependencies {
                provide { mockk<MaskinportenTokenClient>(relaxed = true) }
                provide { mockk<AzuredTokenClient>(relaxed = true) }
                provide { MQListener.connectionFactory }
                provide<Queue>(name = "forespoerselQueue") {
                    ActiveMQQueue(PropertiesConfig.getMQProperties().fraForSystemQueue)
                }
                provide<Queue>(name = "leveransekoeOppdragZSkattekort") {
                    ActiveMQQueue(PropertiesConfig.getMQProperties().leveransekoeOppdragZSkattekort)
                }
            }
        }
        module(testEnvironmentConfig(authServer))
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

    private fun testEnvironmentConfig(authServer: MockOAuth2Server): MapApplicationConfig =
        MapApplicationConfig().apply {
            put("APPLICATION_ENV", "TEST")

            // Database properties
            put("DB_USERNAME", DbListener.container.username)
            put("DB_PASSWORD", DbListener.container.password)
            put("DB_DATABASE", DbListener.container.databaseName)
            put("DB_PORT", DbListener.container.firstMappedPort.toString())
            put("DB_HOST", DbListener.container.host)
            put("AZURE_APP_CLIENT_ID", "default")
            put("AZURE_APP_WELL_KNOWN_URL", authServer.wellKnownUrl("default").toUrl().toString())
        }

    private fun DependencyInjectionConfig.configureShutdownBehavior() {
        conflictPolicy =
            DependencyConflictPolicy { _, _ ->
                DependencyConflictResult.KeepPrevious
            }

        onShutdown = { dependencyKey, instance ->
            when (instance) {
                // Vi ønsker bare en DataSource i bruk for en hel test-kjøring, selv om flere tester start/stopper applikasjonen
                // dette er en opt-out av auto-close-greiene til Kotlins DI-extension:
                is DataSource -> {}
                is ConnectionFactory -> {}
                is AutoCloseable -> instance.close()
            }
        }
    }
}
