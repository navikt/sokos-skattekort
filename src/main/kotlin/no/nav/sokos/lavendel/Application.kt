package no.nav.sokos.lavendel

import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.util.AttributeKey

import no.nav.sokos.lavendel.config.ApplicationState
import no.nav.sokos.lavendel.config.DatabaseConfig
import no.nav.sokos.lavendel.config.DatabaseMigrator
import no.nav.sokos.lavendel.config.PropertiesConfig
import no.nav.sokos.lavendel.config.applicationLifecycleConfig
import no.nav.sokos.lavendel.config.commonConfig
import no.nav.sokos.lavendel.config.configFrom
import no.nav.sokos.lavendel.config.routingConfig
import no.nav.sokos.lavendel.config.securityConfig

fun main() {
    embeddedServer(Netty, port = 8080, module = Application::module).start(true)
}

fun Application.module(appConfig: ApplicationConfig = environment.config) {
    val config = resolveConfig(appConfig)
    DatabaseConfig.init(config, isLocal = config.applicationProperties.profile == PropertiesConfig.Profile.LOCAL)

//    val useAuthentication = PropertiesConfig.Configuration().useAuthentication
    val applicationState = ApplicationState()

    DatabaseMigrator(DatabaseConfig.adminDataSource, config().postgresProperties.adminRole)

    commonConfig()
    applicationLifecycleConfig(applicationState)
    securityConfig()
    routingConfig(applicationState)
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
