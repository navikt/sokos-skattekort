package no.nav.sokos.skattekort.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route

import no.nav.sokos.skattekort.security.MaskinportenTokenClient

fun Route.maksinportenTokenApi(maskinportenTokenClient: MaskinportenTokenClient) {
    route("api/v1") {
        get("maskinportToken") {
            call.respond(HttpStatusCode.OK, maskinportenTokenClient.getAccessToken())
        }
    }
}
