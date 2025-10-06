package no.nav.sokos.skattekort

import com.ibm.mq.jakarta.jms.MQQueue
import io.ktor.server.application.Application
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import mu.KotlinLogging

import no.nav.sokos.skattekort.config.ApplicationState
import no.nav.sokos.skattekort.config.DatabaseConfig
import no.nav.sokos.skattekort.config.MQConfig
import no.nav.sokos.skattekort.config.PropertiesConfig
import no.nav.sokos.skattekort.config.applicationLifecycleConfig
import no.nav.sokos.skattekort.config.commonConfig
import no.nav.sokos.skattekort.config.routingConfig
import no.nav.sokos.skattekort.config.securityConfig
import no.nav.sokos.skattekort.domain.forespoersel.ForespoerselListener
import no.nav.sokos.skattekort.domain.forespoersel.ForespoerselService
import no.nav.sokos.skattekort.domain.person.PersonService

fun main() {
    embeddedServer(Netty, port = 8080, module = Application::module).start(true)
}

private val logger = KotlinLogging.logger {}

fun Application.module(applicationConfig: ApplicationConfig = environment.config) {
    PropertiesConfig.initEnvConfig(applicationConfig)
    val applicationProperties = PropertiesConfig.getApplicationProperties()
    val useAuthentication = applicationProperties.useAuthentication

    val applicationState = ApplicationState()
    applicationLifecycleConfig(applicationState)

    DatabaseConfig.migrate()
    val personService = PersonService(DatabaseConfig.dataSource)
    val forespoerselService = ForespoerselService(DatabaseConfig.dataSource, personService)
    val forespoerselListener =
        ForespoerselListener(
            jmsConnectionFactory = MQConfig.connectionFactory,
            forespoerselService = forespoerselService,
            forespoerselQueue = MQQueue(PropertiesConfig.getMQProperties().fraForSystemQueue),
        )

    if (applicationProperties.mqListenerEnabled) {
        forespoerselListener.start()
    }

    logger.info { "Application started with environment: ${applicationProperties.environment}, useAuthentication: $useAuthentication, mqListenerEnabled: ${applicationProperties.mqListenerEnabled}" }

    commonConfig()
    securityConfig(useAuthentication)
    routingConfig(useAuthentication, applicationState)
}
