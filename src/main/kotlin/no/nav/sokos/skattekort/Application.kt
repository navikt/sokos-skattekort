package no.nav.sokos.skattekort

import com.ibm.mq.jakarta.jms.MQQueue
import io.ktor.server.application.Application
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.di.dependencies
import jakarta.jms.Queue
import mu.KotlinLogging

import no.nav.sokos.skattekort.config.ApplicationState
import no.nav.sokos.skattekort.config.DatabaseConfig
import no.nav.sokos.skattekort.config.PropertiesConfig
import no.nav.sokos.skattekort.config.applicationLifecycleConfig
import no.nav.sokos.skattekort.config.commonConfig
import no.nav.sokos.skattekort.config.routingConfig
import no.nav.sokos.skattekort.config.securityConfig
import no.nav.sokos.skattekort.domain.forespoersel.ForespoerselListener
import no.nav.sokos.skattekort.domain.forespoersel.ForespoerselService
import no.nav.sokos.skattekort.domain.person.PersonService

fun main() {
    embeddedServer(Netty, port = 8080) {
        module()
        if (PropertiesConfig.getApplicationProperties().environment == PropertiesConfig.Environment.TEST) {
            val forespoerselListener: ForespoerselListener by dependencies
            forespoerselListener.start()
        }
    }.start(wait = true)
}

private val logger = KotlinLogging.logger {}

fun Application.module(applicationConfig: ApplicationConfig = environment.config) {
    val applicationState = ApplicationState()
    applicationLifecycleConfig(applicationState)

    PropertiesConfig.initEnvConfig(applicationConfig)
    val applicationProperties = PropertiesConfig.getApplicationProperties()
    val useAuthentication = applicationProperties.useAuthentication

    logger.info { "Application started with environment: ${applicationProperties.environment}, useAuthentication: $useAuthentication" }

    // Kan ikke start opp MQ under TEST pga EmbeddedActiveMQ start opp ikke med MQConfig innstillinger
    if (applicationProperties.environment == PropertiesConfig.Environment.TEST) {
        DatabaseConfig.migrate()
        dependencies {
            provide { DatabaseConfig.dataSource }
            provide<Queue>(name = "forespoerselQueue") {
                MQQueue(PropertiesConfig.getMQProperties().fraForSystemQueue)
            }
            provide(PersonService::class)
            provide(ForespoerselService::class)
            provide(ForespoerselListener::class)
        }
    }

    commonConfig()
    securityConfig(useAuthentication)
    routingConfig(useAuthentication, applicationState)
}
