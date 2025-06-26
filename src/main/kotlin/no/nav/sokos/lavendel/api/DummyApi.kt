package no.nav.sokos.lavendel.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route

import no.nav.sokos.lavendel.service.DummyService

fun Route.dummyApi(dummyService: DummyService = DummyService()) {
    route("/api/v1/") {
        get("hello") {
            val response = dummyService.sayHello()
            call.respond(HttpStatusCode.OK, response)
        }
    }
}
