package no.nav.sokos.skattekort.api

import io.github.resilience4j.core.functions.Either
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route

import no.nav.sokos.skattekort.api.model.HentNavnRequest
import no.nav.sokos.skattekort.api.model.HentSkattekortRequest
import no.nav.sokos.skattekort.api.model.OpprettSkattekortRequest
import no.nav.sokos.skattekort.api.model.WrappedWithErrorResponse
import no.nav.sokos.skattekort.dto.v2.SkattekortDTO
import no.nav.sokos.skattekort.infrastructure.pdl.PdlService
import no.nav.sokos.skattekort.security.AuthorizationException
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
            val skattekortAnswer =
                if (request.hentAlle) {
                    skattekortService.getSkattekort(request.fnr, request.inntektsaar, saksbehandler)
                } else {
                    skattekortService.getSingleSkattekortForEachYear(request.fnr, request.inntektsaar, saksbehandler)
                }
            when (skattekortAnswer) {
                is Either.Left -> throw AuthorizationException("Mangler rettigheter til å se informasjon!")
                is Either.Right ->
                    call.respond(
                        skattekortAnswer.get().map {
                            no.nav.sokos.skattekort.dto
                                .SkattekortDTO(it)
                        },
                    )
            }
        }

        post("opprett") {
            call.requirePermission(Scope.OPPRETT_SCOPE, Role.OPPRETT_ROLE)
            val request = call.receive<OpprettSkattekortRequest>()
            val saksbehandler = call.getNavIdentOrNull()?.let { Saksbehandler(it) }
            skattekortService.createManuelSkattekort(
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
            val skattekortAnswer =
                if (request.hentAlle) {
                    skattekortService.getSkattekort(request.fnr, request.inntektsaar, saksbehandler)
                } else {
                    skattekortService.getSingleSkattekortForEachYear(request.fnr, request.inntektsaar, saksbehandler)
                }
            when (skattekortAnswer) {
                is Either.Left -> call.respond(WrappedWithErrorResponse(data = "", errorMessage = "Mangler rettigheter til å se informasjon!"))
                is Either.Right -> call.respond(WrappedWithErrorResponse(data = skattekortAnswer.get().map(::SkattekortDTO)))
            }
        }

        post("hent-navn") {
            call.requireScope(Scope.HENT_NAVN_SCOPE)
            val saksbehandler = call.getNavIdentOrNull()?.let { Saksbehandler(it) }
            requireNotNull(saksbehandler) { "Missing NAVident in private claims" }

            val reqeust: HentNavnRequest = call.receive()
            when (val result = pdlService.getPersonNavn(reqeust.fnr, saksbehandler)) {
                is Either.Left -> call.respond(WrappedWithErrorResponse(data = "", errorMessage = "Mangler rettigheter til å se informasjon!"))
                is Either.Right -> call.respond(WrappedWithErrorResponse(data = result.get()))
            }
        }

        post("opprett") {
            call.requirePermission(Scope.OPPRETT_SCOPE, Role.OPPRETT_ROLE)
            val request = call.receive<OpprettSkattekortRequest>()
            val saksbehandler = call.getNavIdentOrNull()?.let { Saksbehandler(it) }
            skattekortService.createManuelSkattekort(
                request.fnr,
                request.skattekort,
                saksbehandler,
            )
            call.respond(HttpStatusCode.Created)
        }
    }
}
