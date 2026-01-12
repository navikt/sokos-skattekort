package no.nav.sokos.skattekort.api

import kotlinx.serialization.Serializable

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import mu.KotlinLogging

import no.nav.sokos.skattekort.config.TEAM_LOGS_MARKER
import no.nav.sokos.skattekort.module.forespoersel.ForespoerselService
import no.nav.sokos.skattekort.module.skattekort.Status
import no.nav.sokos.skattekort.module.status.StatusService
import no.nav.sokos.skattekort.security.AuthToken.getSaksbehandler

private val logger = KotlinLogging.logger { }

const val BASE_PATH = "/api/v1/skattekort"

fun Route.skattekortApi(
    forespoerselService: ForespoerselService,
    statusService: StatusService,
) {
    route(BASE_PATH) {
        post("bestille") {
            val request = call.receive<ForespoerselRequest>()
            val saksbehandler = getSaksbehandler(call)

            logger.info(marker = TEAM_LOGS_MARKER) { "skattekortApi (${saksbehandler.ident}) - Mottatt forespørsel: $request" }

            val message = "${request.forsystem};${request.aar};${request.personIdent}"
            forespoerselService.taImotForespoersel(message, saksbehandler)
            call.respond(HttpStatusCode.Created)
        }
    }

    route(BASE_PATH) {
        post("status") {
            val request = call.receive<ForespoerselRequest>()
            val saksbehandler = getSaksbehandler(call)

            logger.info(marker = TEAM_LOGS_MARKER) { "skattekortApi (${saksbehandler.ident}) - Ber om status på forespørsel: $request" }

            call.respond(
                StatusResponse(statusService.statusForespoeresel(request.personIdent, request.aar, request.forsystem)),
            )
        }
    }
}

@Serializable
data class StatusResponse(
    val status: Status,
)

@Serializable
data class ForespoerselRequest(
    val personIdent: String,
    val aar: Int,
    val forsystem: String,
)
