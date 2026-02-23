package no.nav.sokos.skattekort.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route

import no.nav.sokos.skattekort.api.model.OpprettSkattekortRequest
import no.nav.sokos.skattekort.api.model.SkattekortPersonRequest
import no.nav.sokos.skattekort.module.skattekort.SkattekortPersonService
import no.nav.sokos.skattekort.security.AuthorizationGuard.getNavIdentOrNull
import no.nav.sokos.skattekort.security.AuthorizationGuard.requirePermission
import no.nav.sokos.skattekort.security.Role
import no.nav.sokos.skattekort.security.Saksbehandler
import no.nav.sokos.skattekort.security.Scope

fun Route.skattekortPersonApi(skattekortPersonService: SkattekortPersonService) {
    route("/api/v1/person") {
        post("hent-skattekort") {
            call.requirePermission(Scope.HENT_SCOPE, Role.HENT_ROLE)
            val skattekortPersonRequest: SkattekortPersonRequest = call.receive()
            val saksbehandler = call.getNavIdentOrNull()?.let { Saksbehandler(it) }
            call.respond(
                skattekortPersonService
                    .hentSingleSkattekortForEachYear(skattekortPersonRequest.fnr, skattekortPersonRequest.inntektsaar, saksbehandler),
            )
        }

        post("opprett") {
            call.requirePermission(Scope.OPPRETT_SCOPE, Role.OPPRETT_ROLE)
            val request = call.receive<OpprettSkattekortRequest>()
            val saksbehandler = call.getNavIdentOrNull()?.let { Saksbehandler(it) }
            skattekortPersonService.opprettSkattekort(
                request.fnr,
                request.skattekort,
                saksbehandler,
            )
            call.respond(HttpStatusCode.Created)
        }
    }
}

fun Route.deprecatedSkattekortPersonApi(skattekortPersonService: SkattekortPersonService) {
    route("/api/v1") {
        post("hent-skattekort") {
            call.requirePermission(Scope.HENT_SCOPE, Role.HENT_ROLE)
            val skattekortPersonRequest: SkattekortPersonRequest = call.receive()
            val saksbehandler = call.getNavIdentOrNull()?.let { Saksbehandler(it) }
            call.respond(
                skattekortPersonService.hentSkattekortPerson(skattekortPersonRequest.fnr, skattekortPersonRequest.inntektsaar, saksbehandler),
            )
        }
    }
}
