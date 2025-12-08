package no.nav.sokos.skattekort.api

import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route

import no.nav.sokos.skattekort.api.skattekortpersonapi.v1.SkattekortPersonRequest
import no.nav.sokos.skattekort.module.skattekort.SkattekortPersonService
import no.nav.sokos.skattekort.security.AuthToken.getSaksbehandler

fun Route.skattekortPersonApi(skattekortPersonService: SkattekortPersonService) {
    route("/api/v1") {
        post("hent-skattekort") {
            val skattekortPersonRequest: SkattekortPersonRequest = call.receive()
            val saksbehandler = getSaksbehandler(call)
            call.respond(
                skattekortPersonService.hentSkattekortPerson(skattekortPersonRequest, saksbehandler),
            )
        }
    }
}
