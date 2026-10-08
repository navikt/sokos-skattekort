package no.nav.sokos.skattekort.config

import io.ktor.server.application.Application
import io.ktor.server.auth.authenticate
import io.ktor.server.routing.routing

import no.nav.sokos.skattekort.api.skattekortAdminApi
import no.nav.sokos.skattekort.api.skattekortApi
import no.nav.sokos.skattekort.api.skattekortPersonApi
import no.nav.sokos.skattekort.api.swaggerApi

fun Application.routingConfig(
    applicationState: ApplicationState,
    services: ApiServices,
    azureAdProperties: PropertiesConfig.AzureAdProperties = PropertiesConfig.azureAdProperties,
) {
    routing {
        internalNaisRoutes(applicationState)
        swaggerApi()
        authenticate(azureAdProperties.providerName) {
            skattekortAdminApi(
                services.bestillingsbatchService,
                services.personService,
                services.utsendingService,
                services.skattekortService,
            )
            skattekortApi(
                services.forespoerselService,
                services.personService,
                services.statusService,
                services.backgroundTaskRunner,
            )
            skattekortPersonApi(services.skattekortService, services.pdlService)
        }
    }
}
