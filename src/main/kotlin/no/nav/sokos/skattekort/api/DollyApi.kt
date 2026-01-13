package no.nav.sokos.skattekort.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import mu.KotlinLogging

private val logger = KotlinLogging.logger { }

const val DOLLY_BASE_PATH = "api/v1/dolly"

fun Route.skattekortDollyApi() {
    route(DOLLY_BASE_PATH) {
        get("hent/{fnr}") {
            call.respond(HttpStatusCode.NotImplemented)
        }
        get("hent/{fnr}/{inntektsaar}") {
            call.respond(HttpStatusCode.NotImplemented)
        }
        post("opprett") {
            call.respond(HttpStatusCode.NotImplemented)
        }
    }
}
