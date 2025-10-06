package no.nav.sokos.skattekort

import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.testing.TestApplicationBuilder
import org.apache.activemq.artemis.jms.client.ActiveMQQueue

import no.nav.sokos.skattekort.config.DatabaseConfig
import no.nav.sokos.skattekort.config.PropertiesConfig
import no.nav.sokos.skattekort.domain.forespoersel.ForespoerselListener
import no.nav.sokos.skattekort.domain.forespoersel.ForespoerselService
import no.nav.sokos.skattekort.domain.person.PersonService
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
            module()

            val personService = PersonService(DatabaseConfig.dataSource)
            val forespoerselService = ForespoerselService(DatabaseConfig.dataSource, personService)
            val forespoerselListener =
                ForespoerselListener(
                    jmsConnectionFactory = MQListener.getConnectionFactory(),
                    forespoerselService = forespoerselService,
                    forespoerselQueue = ActiveMQQueue(PropertiesConfig.getMQProperties().fraForSystemQueue),
                )
            forespoerselListener.start()
        }
    }
}
