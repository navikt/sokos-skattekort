package no.nav.sokos.skattekort

import com.ibm.mq.jakarta.jms.MQQueue
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.util.AttributeKey
import jakarta.jms.ConnectionFactory
import jakarta.jms.Queue

import no.nav.sokos.skattekort.api.BestillingsListener
import no.nav.sokos.skattekort.api.Skattekortbestillingsservice
import no.nav.sokos.skattekort.config.ApplicationState
import no.nav.sokos.skattekort.config.DatabaseConfig
import no.nav.sokos.skattekort.config.DatabaseMigrator
import no.nav.sokos.skattekort.config.MQConfig
import no.nav.sokos.skattekort.config.PropertiesConfig
import no.nav.sokos.skattekort.config.applicationLifecycleConfig
import no.nav.sokos.skattekort.config.commonConfig
import no.nav.sokos.skattekort.config.configFrom
import no.nav.sokos.skattekort.config.routingConfig
import no.nav.sokos.skattekort.config.securityConfig

fun main() {
    embeddedServer(Netty, port = 8080, module = Application::module).start(true)
}

fun Application.module(
    appConfig: ApplicationConfig = environment.config,
    testJmsConnectionFactory: ConnectionFactory? = null,
    testBestillingsQueue: Queue? = null,
) {
    val config: PropertiesConfig.Configuration = resolveConfig(appConfig)
    DatabaseConfig.init(config, isLocal = config.applicationProperties.profile == PropertiesConfig.Profile.LOCAL)
    DatabaseMigrator(DatabaseConfig.adminDataSource, config().postgresProperties.adminRole)
    val bestillingsService = Skattekortbestillingsservice(DatabaseConfig.dataSource)
    val bestillingsListener =
        if (testJmsConnectionFactory == null) {
            MQConfig.init(config)
            BestillingsListener(MQConfig.connectionFactory, bestillingsService, MQQueue(config.mqProperties.bestilleSkattekortQueueName))
        } else {
            BestillingsListener(testJmsConnectionFactory, bestillingsService, testBestillingsQueue!!)
        }

    val applicationState = ApplicationState()
    commonConfig()
    applicationLifecycleConfig(applicationState)
    securityConfig()
    routingConfig(config.applicationProperties.useAuthentication, applicationState)
}

val ConfigAttributeKey = AttributeKey<PropertiesConfig.Configuration>("config")

fun Application.config(): PropertiesConfig.Configuration = this.attributes[ConfigAttributeKey]

fun ApplicationCall.config(): PropertiesConfig.Configuration = this.application.config()

fun Application.resolveConfig(appConfig: ApplicationConfig = environment.config): PropertiesConfig.Configuration =
    if (attributes.contains(ConfigAttributeKey)) {
        // Bruk config hvis den allerede er satt
        this.config()
    } else {
        configFrom(appConfig).also {
            attributes.put(ConfigAttributeKey, it)
        }
    }
