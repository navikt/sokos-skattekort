package no.nav.sokos.skattekort.config

import io.ktor.server.application.Application
import io.ktor.server.auth.authenticate
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.routing.Route
import io.ktor.server.routing.routing

import no.nav.sokos.skattekort.api.foerespoerselApi
import no.nav.sokos.skattekort.api.maskinportenTokenApi
import no.nav.sokos.skattekort.module.forespoersel.ForespoerselService
import no.nav.sokos.skattekort.security.MaskinportenTokenClient

fun Application.routingConfig(
    useAuthentication: Boolean,
    applicationState: ApplicationState,
) {
    routing {
        internalNaisRoutes(applicationState)
        // TODO: Midlertidig kode for testing. Må ikke puttes i produksjon!
        authenticate(useAuthentication, AUTHENTICATION_NAME) {
            val maskinportenTokenClient: MaskinportenTokenClient by dependencies
            val forespoerselService: ForespoerselService by dependencies

            maskinportenTokenApi(maskinportenTokenClient)
            foerespoerselApi(forespoerselService)
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
