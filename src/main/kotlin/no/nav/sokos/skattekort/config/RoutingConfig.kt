package no.nav.sokos.skattekort.config

import io.ktor.server.application.Application
import io.ktor.server.auth.authenticate
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.routing.routing

import no.nav.sokos.skattekort.api.skattekortApi
import no.nav.sokos.skattekort.api.skattekortPersonApi
import no.nav.sokos.skattekort.api.swaggerApi
import no.nav.sokos.skattekort.module.forespoersel.ForespoerselService
import no.nav.sokos.skattekort.module.skattekort.SkattekortPersonService
import no.nav.sokos.skattekort.module.status.StatusService

fun Application.routingConfig(
    applicationState: ApplicationState,
    azureAdProperties: PropertiesConfig.AzureAdProperties = PropertiesConfig.AzureAdProperties(),
) {
    routing {
        internalNaisRoutes(applicationState)
        swaggerApi()
        authenticate(azureAdProperties.providerName) {
            val forespoerselService: ForespoerselService by dependencies
            val skattekortPersonService: SkattekortPersonService by dependencies
            val statusService: StatusService by dependencies

            skattekortApi(forespoerselService, statusService)
            skattekortPersonApi(skattekortPersonService)
        }
    }
}
