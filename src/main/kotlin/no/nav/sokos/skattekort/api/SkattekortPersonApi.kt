package no.nav.sokos.skattekort.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route

import no.nav.sokos.skattekort.api.model.HentNavnRequest
import no.nav.sokos.skattekort.api.model.HentNavnResponse
import no.nav.sokos.skattekort.api.model.OpprettSkattekortRequest
import no.nav.sokos.skattekort.api.model.SkattekortPersonRequest
import no.nav.sokos.skattekort.dto.SkattekortDTO
import no.nav.sokos.skattekort.dto.v2.SkattekortResponseDTO
import no.nav.sokos.skattekort.infrastructure.pdl.PdlService
import no.nav.sokos.skattekort.module.skattekort.SkattekortPersonService
import no.nav.sokos.skattekort.security.AuthorizationGuard.getNavIdentOrNull
import no.nav.sokos.skattekort.security.AuthorizationGuard.requirePermission
import no.nav.sokos.skattekort.security.AuthorizationGuard.requireScope
import no.nav.sokos.skattekort.security.Role
import no.nav.sokos.skattekort.security.Saksbehandler
import no.nav.sokos.skattekort.security.Scope

fun Route.skattekortPersonApi(
    skattekortPersonService: SkattekortPersonService,
    pdlService: PdlService,
) {
    route("/api/v1/person") {
        post("hent-skattekort") {
            call.requirePermission(Scope.HENT_SKATTEKORT_SCOPE, Role.HENT_SKATTEKORT_ROLE)
            val skattekortPersonRequest: SkattekortPersonRequest = call.receive()
            val saksbehandler = call.getNavIdentOrNull()?.let { Saksbehandler(it) }
            if (skattekortPersonRequest.hentAlle) {
                call.respond(
                    skattekortPersonService
                        .hentSkattekortPerson(skattekortPersonRequest.fnr, skattekortPersonRequest.inntektsaar, saksbehandler)
                        .map(::SkattekortDTO),
                )
            } else {
                call.respond(
                    skattekortPersonService
                        .hentSingleSkattekortForEachYear(skattekortPersonRequest.fnr, skattekortPersonRequest.inntektsaar, saksbehandler)
                        .map(::SkattekortDTO),
                )
            }
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
    route("/api/v2/person") {
        post("hent-skattekort") {
            call.requirePermission(Scope.HENT_SKATTEKORT_SCOPE, Role.HENT_SKATTEKORT_ROLE)
            val skattekortPersonRequest: SkattekortPersonRequest = call.receive()
            val saksbehandler = call.getNavIdentOrNull()?.let { Saksbehandler(it) }
            if (skattekortPersonRequest.hentAlle) {
                call.respond(
                    skattekortPersonService
                        .hentSkattekortPerson(
                            skattekortPersonRequest.fnr,
                            skattekortPersonRequest.inntektsaar,
                            saksbehandler,
                        ).map(::SkattekortResponseDTO),
                )
            } else {
                call.respond(
                    skattekortPersonService
                        .hentSingleSkattekortForEachYear(skattekortPersonRequest.fnr, skattekortPersonRequest.inntektsaar, saksbehandler)
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
            skattekortPersonService.opprettSkattekort(
                request.fnr,
                request.skattekort,
                saksbehandler,
            )
            call.respond(HttpStatusCode.Created)
        }
    }
}
