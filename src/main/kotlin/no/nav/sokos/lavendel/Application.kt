package no.nav.sokos.lavendel

import io.ktor.server.application.Application
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty

import no.nav.sokos.lavendel.config.ApplicationState
import no.nav.sokos.lavendel.config.PropertiesConfig
import no.nav.sokos.lavendel.config.applicationLifecycleConfig
import no.nav.sokos.lavendel.config.commonConfig
import no.nav.sokos.lavendel.config.routingConfig
import no.nav.sokos.lavendel.config.securityConfig

fun main() {
    embeddedServer(Netty, port = 8080, module = Application::module).start(true)
}

fun Application.module() {
    val useAuthentication = PropertiesConfig.Configuration().useAuthentication
    val applicationState = ApplicationState()

    commonConfig()
    applicationLifecycleConfig(applicationState)
    securityConfig(useAuthentication)
    routingConfig(useAuthentication, applicationState)
}
