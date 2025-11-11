package no.nav.sokos.skattekort

import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.stream.Collectors
import javax.sql.DataSource

import kotlin.time.Duration.Companion.seconds

import io.kotest.assertions.nondeterministic.eventuallyConfig
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.plugins.di.DI
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.TestApplicationBuilder
import io.ktor.server.testing.testApplication
import io.mockk.mockk
import jakarta.jms.ConnectionFactory
import jakarta.jms.Queue
import kotliquery.queryOf
import org.apache.activemq.artemis.jms.client.ActiveMQQueue

import no.nav.security.mock.oauth2.withMockOAuth2Server
import no.nav.sokos.skattekort.config.PropertiesConfig
import no.nav.sokos.skattekort.config.SftpConfig
import no.nav.sokos.skattekort.listener.DbListener
import no.nav.sokos.skattekort.listener.MQListener
import no.nav.sokos.skattekort.listener.SftpListener
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
                configureTestEnvironment()
                configureTestApplication()
                startApplication()
                thunk()
            }
        }

    fun TestApplicationBuilder.configureTestEnvironment() {
        environment {
            System.setProperty("APPLICATION_ENV", "TEST")
            config =
                MapApplicationConfig().apply {
                    // Database properties
                    put("POSTGRES_USER_USERNAME", DbListener.container.username)
                    put("POSTGRES_USER_PASSWORD", DbListener.container.password)
                    put("POSTGRES_ADMIN_USERNAME", DbListener.container.username)
                    put("POSTGRES_ADMIN_PASSWORD", DbListener.container.password)
                    put("POSTGRES_NAME", DbListener.container.databaseName)
                    put("POSTGRES_PORT", DbListener.container.firstMappedPort.toString())
                    put("POSTGRES_HOST", DbListener.container.host)
                }
        }
    }

    fun TestApplicationBuilder.configureTestApplication() {
        install(DI) {
            onShutdown = { dependencyKey, instance ->
                when (instance) {
                    // Vi ønsker ikke DataSource eller ConnectionFactory lukket automatisk under testApplication kjøring.
                    // dette er en opt-out av auto-close-greiene til Kotlins DI-extension:
                    is DataSource -> {}
                    is ConnectionFactory -> {}
                    is AutoCloseable -> instance.close()
                }
            }
        }

        application {
            dependencies {
                provide { SftpConfig(SftpListener.sftpProperties) }
                provide { mockk<MaskinportenTokenClient>() }
                provide<ConnectionFactory> { MQListener.connectionFactory }
                provide<Queue>(name = "forespoerselQueue") {
                    ActiveMQQueue(PropertiesConfig.getMQProperties().fraForSystemQueue)
                }
                provide<Queue>(name = "leveransekoeOppdragZSkattekort") {
                    ActiveMQQueue(PropertiesConfig.getMQProperties().leveransekoeOppdragZSkattekort)
                }
            }
            module()
        }
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

    fun inATransaction(thunk: (tx: kotliquery.TransactionalSession) -> Unit) {
        DbListener.dataSource.transaction { tx ->
            thunk(tx)
        }
    }
}
