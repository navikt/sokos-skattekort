package no.nav.sokos.skattekort.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import mu.KotlinLogging

import no.nav.sokos.skattekort.api.model.ForespoerselRequest
import no.nav.sokos.skattekort.api.model.StatusResponse
import no.nav.sokos.skattekort.config.TEAM_LOGS_MARKER
import no.nav.sokos.skattekort.forespoersel.ForespoerselService
import no.nav.sokos.skattekort.security.AuthorizationGuard.getNavIdentOrNull
import no.nav.sokos.skattekort.security.AuthorizationGuard.requirePermission
import no.nav.sokos.skattekort.security.AuthorizationGuard.requireScope
import no.nav.sokos.skattekort.security.Role
import no.nav.sokos.skattekort.security.Saksbehandler
import no.nav.sokos.skattekort.security.Scope
import no.nav.sokos.skattekort.skattekortbestilling.StatusService

private val logger = KotlinLogging.logger { }

const val BASE_PATH_SKATTEKORT = "/api/v1/skattekort"

fun Route.skattekortApi(
    forespoerselService: ForespoerselService,
    statusService: StatusService,
) {
    route(BASE_PATH_SKATTEKORT) {
        post("bestille") {
            call.requirePermission(requiredScope = Scope.BESTILLE_SCOPE, requiredRole = Role.BESTILLE_ROLE)
            val request = call.receive<ForespoerselRequest>()
            val saksbehandler = call.getNavIdentOrNull()?.let { Saksbehandler(it) }

            logger.info(marker = TEAM_LOGS_MARKER) {
                "skattekortApi - Mottatt forespørsel: $request på vegne av ${saksbehandler?.ident}"
            }

            val message = "${request.forsystem};${request.aar};${request.personIdent}"
            forespoerselService.taImotForespoersel(message, saksbehandler)
            call.respond(HttpStatusCode.Created)
        }
        post("status") {
            call.requireScope(Scope.STATUS_SCOPE)
            val request = call.receive<ForespoerselRequest>()
            val saksbehandler = call.getNavIdentOrNull()?.let { Saksbehandler(it) }

            logger.info(marker = TEAM_LOGS_MARKER) {
                "skattekortApi - Mottatt forespørsel: $request på vegne av ${saksbehandler?.ident}"
            }
            call.respond(
                StatusResponse(statusService.statusForespoeresel(request.personIdent, request.aar, request.forsystem)),
            )
        }
    }
}
