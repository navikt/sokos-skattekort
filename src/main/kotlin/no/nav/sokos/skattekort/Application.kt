package no.nav.sokos.skattekort

import com.ibm.mq.jakarta.jms.MQQueue
import io.ktor.server.application.Application
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty

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

fun Application.module() {
    val applicationProperties = PropertiesConfig.ApplicationProperties()
    if (applicationProperties.environment == PropertiesConfig.Environment.LOCAL) {
        DatabaseConfig.migrate()

        val personService = PersonService(DatabaseConfig.dataSource)
        val forespoerselService = ForespoerselService(DatabaseConfig.dataSource, personService)
        val forespoerselListener = ForespoerselListener(MQConfig.connectionFactory, forespoerselService, MQQueue(PropertiesConfig.MQProperties().fraForSystemQueue))
        forespoerselListener.start()
    }

    val applicationState = ApplicationState()
    commonConfig()
    applicationLifecycleConfig(applicationState)
    securityConfig(applicationProperties.useAuthentication)
    routingConfig(applicationProperties.useAuthentication, applicationState)
}
