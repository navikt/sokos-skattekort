package no.nav.sokos.skattekort

import io.ktor.server.application.Application
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import mu.KotlinLogging

import no.nav.sokos.skattekort.config.ApplicationState
import no.nav.sokos.skattekort.config.DatabaseConfig
import no.nav.sokos.skattekort.config.PropertiesConfig
import no.nav.sokos.skattekort.config.applicationLifecycleConfig
import no.nav.sokos.skattekort.config.commonConfig
import no.nav.sokos.skattekort.config.routingConfig
import no.nav.sokos.skattekort.config.securityConfig

fun main() {
    embeddedServer(Netty, port = 8080, module = Application::module).start(true)
}

private val logger = KotlinLogging.logger {}

fun Application.module(applicationConfig: ApplicationConfig = environment.config) {
    PropertiesConfig.initEnvConfig(applicationConfig)
    val applicationProperties = PropertiesConfig.getApplicationProperties()
    val useAuthentication = applicationProperties.useAuthentication
    if (applicationProperties.environment == PropertiesConfig.Environment.TEST) {
        DatabaseConfig.migrate()

        // Kan ikke start opp MQ under TEST pga EmbeddedActiveMQ start opp ikke med MQConfig innstillinger
        // val personService = PersonService(DatabaseConfig.dataSource)
        // val forespoerselService = ForespoerselService(DatabaseConfig.dataSource, personService)
        // val forespoerselListener = ForespoerselListener(MQConfig.connectionFactory, forespoerselService, MQQueue(PropertiesConfig.getMQProperties().fraForSystemQueue))
        // forespoerselListener.start()
    }

    val applicationState = ApplicationState()
    commonConfig()
    securityConfig(useAuthentication)
    routingConfig(useAuthentication, applicationState)
    applicationLifecycleConfig(applicationState)

    logger.info { "Application started with environment: ${applicationProperties.environment}, useAuthentication: $useAuthentication" }
}
