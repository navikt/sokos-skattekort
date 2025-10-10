package no.nav.sokos.skattekort.config

import io.ktor.server.application.Application
import io.ktor.server.auth.authenticate
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.routing.Route
import io.ktor.server.routing.routing

import no.nav.sokos.skattekort.api.maksinportenTokenApi
import no.nav.sokos.skattekort.security.MaskinportenTokenClient

fun Application.routingConfig(
    useAuthentication: Boolean,
    applicationState: ApplicationState,
) {
    routing {
        internalNaisRoutes(applicationState)
        authenticate(useAuthentication, AUTHENTICATION_NAME) {
            val maskinportenTokenClient: MaskinportenTokenClient by dependencies
            maksinportenTokenApi(maskinportenTokenClient)
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
