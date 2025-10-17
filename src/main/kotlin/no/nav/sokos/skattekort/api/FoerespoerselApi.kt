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

private val logger = KotlinLogging.logger { }

fun Route.foerespoerselApi(forespoerselService: ForespoerselService) {
    route("api/v1") {
        post("bestilleSkattekort") {
            val request = call.receive<FoerespoerselRequest>()
            val message = "${request.forsystem};${request.aar};${request.personIdent}"

            logger.info(marker = TEAM_LOGS_MARKER) { "foerespoerselApi - Mottat request: $request" }
            forespoerselService.taImotForespoersel(message)
            call.respond(HttpStatusCode.Created)
        }
    }
}

@Serializable
data class FoerespoerselRequest(
    val personIdent: String,
    val aar: Int,
    val forsystem: String,
)
