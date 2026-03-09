package no.nav.sokos.skattekort.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route

import no.nav.sokos.skattekort.api.model.HentNavnRequest
import no.nav.sokos.skattekort.api.model.HentNavnResponse
import no.nav.sokos.skattekort.api.model.HentSkattekortRequest
import no.nav.sokos.skattekort.api.model.OpprettSkattekortRequest
import no.nav.sokos.skattekort.dto.SkattekortDTO
import no.nav.sokos.skattekort.dto.v2.SkattekortResponseDTO
import no.nav.sokos.skattekort.infrastructure.pdl.PdlService
import no.nav.sokos.skattekort.security.AuthorizationGuard.getNavIdentOrNull
import no.nav.sokos.skattekort.security.AuthorizationGuard.requirePermission
import no.nav.sokos.skattekort.security.AuthorizationGuard.requireScope
import no.nav.sokos.skattekort.security.Role
import no.nav.sokos.skattekort.security.Saksbehandler
import no.nav.sokos.skattekort.security.Scope
import no.nav.sokos.skattekort.skattekort.SkattekortService

fun Route.skattekortPersonApi(
    skattekortService: SkattekortService,
    pdlService: PdlService,
) {
    route("/api/v1/person") {
        post("hent-skattekort") {
            call.requirePermission(Scope.HENT_SKATTEKORT_SCOPE, Role.HENT_SKATTEKORT_ROLE)
            val request: HentSkattekortRequest = call.receive()
            val saksbehandler = call.getNavIdentOrNull()?.let { Saksbehandler(it) }
            if (request.hentAlle) {
                call.respond(skattekortService.getSkattekort(request.fnr, request.inntektsaar, saksbehandler).map(::SkattekortDTO))
            } else {
                call.respond(skattekortService.getSingleSkattekortForEachYear(request.fnr, request.inntektsaar, saksbehandler).map(::SkattekortDTO))
            }
        }

        post("opprett") {
            call.requirePermission(Scope.OPPRETT_SCOPE, Role.OPPRETT_ROLE)
            val request = call.receive<OpprettSkattekortRequest>()
            val saksbehandler = call.getNavIdentOrNull()?.let { Saksbehandler(it) }
            skattekortService.createSkattekort(
                request.fnr,
                request.skattekort,
                saksbehandler,
            )
            call.respond(HttpStatusCode.Created)
        }
    }
    route("/api/v2/person") {
        post("hent-skattekort") {
            call.requirePermission(Scope.HENT_SKATTEKORT_SCOPE, Role.HENT_SKATTEKORT_ROLE)
            val request: HentSkattekortRequest = call.receive()
            val saksbehandler = call.getNavIdentOrNull()?.let { Saksbehandler(it) }
            if (request.hentAlle) {
                call.respond(
                    skattekortService
                        .getSkattekort(
                            request.fnr,
                            request.inntektsaar,
                            saksbehandler,
                        ).map(::SkattekortResponseDTO),
                )
            } else {
                call.respond(
                    skattekortService
                        .getSingleSkattekortForEachYear(request.fnr, request.inntektsaar, saksbehandler)
                        .map(::SkattekortResponseDTO),
                )
            }
        }

        post("hent-navn") {
            call.requireScope(Scope.HENT_NAVN_SCOPE)
            val saksbehandler = call.getNavIdentOrNull()?.let { Saksbehandler(it) }
            requireNotNull(saksbehandler) { "Missing NAVident in private claims" }

            val reqeust: HentNavnRequest = call.receive()
            pdlService.getPersonNavn(reqeust.fnr, saksbehandler)?.let {
                call.respond(HentNavnResponse(it))
            } ?: call.respond(HttpStatusCode.OK)
        }

        post("opprett") {
            call.requirePermission(Scope.OPPRETT_SCOPE, Role.OPPRETT_ROLE)
            val request = call.receive<OpprettSkattekortRequest>()
            val saksbehandler = call.getNavIdentOrNull()?.let { Saksbehandler(it) }
            skattekortService.createSkattekort(
                request.fnr,
                request.skattekort,
                saksbehandler,
            )
            call.respond(HttpStatusCode.Created)
        }
    }
}
