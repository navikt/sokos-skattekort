package no.nav.sokos.skattekort.config

import io.ktor.server.application.Application
import io.ktor.server.auth.authenticate
import io.ktor.server.routing.Route
import io.ktor.server.routing.routing

import no.nav.sokos.skattekort.api.swaggerApi

fun Application.routingConfig(
    useAuthentication: Boolean,
    applicationState: ApplicationState,
) {
    routing {
        internalNaisRoutes(applicationState)
        swaggerApi()
        authenticate(useAuthentication, AUTHENTICATION_NAME) {
//            val forespoerselService: ForespoerselService by dependencies
//            val skattekortPersonService: SkattekortPersonService by dependencies
//
//            skattekortApi(forespoerselService)
//            skattekortPersonApi(skattekortPersonService)
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
