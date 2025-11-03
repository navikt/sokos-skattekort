package no.nav.sokos.skattekort.config

import io.ktor.server.application.Application
import io.ktor.server.auth.authenticate
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.routing.Route
import io.ktor.server.routing.routing

import no.nav.sokos.skattekort.api.maskinportenTokenApi
import no.nav.sokos.skattekort.api.skattekortApi
import no.nav.sokos.skattekort.api.skattekortPersonApi
import no.nav.sokos.skattekort.api.swaggerApi
import no.nav.sokos.skattekort.module.forespoersel.ForespoerselService
import no.nav.sokos.skattekort.module.skattekortpersonapi.v1.SkattekortPersonService
import no.nav.sokos.skattekort.security.MaskinportenTokenClient

fun Application.routingConfig(
    useAuthentication: Boolean,
    applicationState: ApplicationState,
) {
    routing {
        internalNaisRoutes(applicationState)
        swaggerApi()
        authenticate(useAuthentication, AUTHENTICATION_NAME) {
            val maskinportenTokenClient: MaskinportenTokenClient by dependencies
            val forespoerselService: ForespoerselService by dependencies
            val skattekortPersonService: SkattekortPersonService by dependencies

            maskinportenTokenApi(maskinportenTokenClient)
            skattekortApi(forespoerselService)
            skattekortPersonApi(skattekortPersonService)
        }
    }
}

fun Route.authenticate(
    useAuthentication: Boolean,
    authenticationProviderId: String? = null,
    block: Route.() -> Unit,
) {
    if (useAuthentication) authenticate(authenticationProviderId) { block() } else block()
}
