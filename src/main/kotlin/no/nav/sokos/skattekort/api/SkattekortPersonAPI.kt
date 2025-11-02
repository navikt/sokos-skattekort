package no.nav.sokos.skattekort.api

import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route

import no.nav.sokos.skattekort.module.skattekortpersonapi.v1.SkattekortPersonRequest
import no.nav.sokos.skattekort.module.skattekortpersonapi.v1.SkattekortPersonService

fun Route.skattekortPersonApi(skattekortPersonService: SkattekortPersonService) {
    route("/api/hent-skattekort") {
        post("1") {
            val skattekortPersonRequest: SkattekortPersonRequest = call.receive()
            call.respond(
                skattekortPersonService.hentSkattekortPerson(skattekortPersonRequest),
            )
        }
    }
}
