package no.nav.sokos.skattekort

import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.testing.TestApplicationBuilder
import jakarta.jms.ConnectionFactory
import jakarta.jms.Queue
import org.apache.activemq.artemis.jms.client.ActiveMQQueue

import no.nav.sokos.skattekort.config.PropertiesConfig
import no.nav.sokos.skattekort.domain.forespoersel.ForespoerselListener
import no.nav.sokos.skattekort.listener.DbListener
import no.nav.sokos.skattekort.listener.MQListener

object TestUtil {
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
        application {
            dependencies {
                provide<ConnectionFactory> { MQListener.getConnectionFactory() }
                provide<Queue>(name = "forespoerselQueue") {
                    ActiveMQQueue(PropertiesConfig.getMQProperties().fraForSystemQueue)
                }
            }
            module()

            val forespoerselListener: ForespoerselListener by dependencies
            forespoerselListener.start()
        }
    }
}
