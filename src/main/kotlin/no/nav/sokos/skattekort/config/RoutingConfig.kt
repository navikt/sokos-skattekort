package no.nav.sokos.skattekort.config

import io.ktor.server.application.Application
import io.ktor.server.auth.authenticate
import io.ktor.server.routing.routing

import no.nav.sokos.skattekort.api.skattekortAdminApi
import no.nav.sokos.skattekort.api.skattekortApi
import no.nav.sokos.skattekort.api.skattekortPersonApi
import no.nav.sokos.skattekort.api.swaggerApi
import no.nav.sokos.skattekort.forespoersel.ForespoerselService
import no.nav.sokos.skattekort.infrastructure.pdl.PdlService
import no.nav.sokos.skattekort.person.PersonService
import no.nav.sokos.skattekort.skattekort.SkattekortService
import no.nav.sokos.skattekort.skattekortbestilling.BestillingsbatchService
import no.nav.sokos.skattekort.skattekortbestilling.StatusService
import no.nav.sokos.skattekort.utsending.UtsendingService

fun Application.routingConfig(
    applicationState: ApplicationState,
    bestillingsbatchService: BestillingsbatchService,
    forespoerselService: ForespoerselService,
    pdlService: PdlService,
    personService: PersonService,
    skattekortService: SkattekortService,
    statusService: StatusService,
    utsendingService: UtsendingService,
    azureAdProperties: PropertiesConfig.AzureAdProperties = PropertiesConfig.azureAdProperties,
) {
    routing {
        internalNaisRoutes(applicationState)
        swaggerApi()
        authenticate(azureAdProperties.providerName) {
            skattekortAdminApi(bestillingsbatchService, personService, utsendingService, skattekortService)
            skattekortApi(forespoerselService, statusService)
            skattekortPersonApi(skattekortService, pdlService)
        }
    }
}
