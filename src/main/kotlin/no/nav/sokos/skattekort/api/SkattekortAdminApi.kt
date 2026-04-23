package no.nav.sokos.skattekort.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.route

import no.nav.sokos.skattekort.api.model.AuditResponse
import no.nav.sokos.skattekort.api.model.BatchInsightRequest
import no.nav.sokos.skattekort.api.model.BatchInsightResponse
import no.nav.sokos.skattekort.api.model.FnrRequest
import no.nav.sokos.skattekort.person.PersonService
import no.nav.sokos.skattekort.security.AuthorizationGuard.requireScope
import no.nav.sokos.skattekort.security.Scope
import no.nav.sokos.skattekort.skattekortbestilling.BestillingsbatchService
import no.nav.sokos.skattekort.skattekortbestilling.Bestillingsreferanse

const val BASE_PATH_ADMIN = "/api/v1/admin"

fun Route.skattekortAdminApi(
    bestillingsbatchService: BestillingsbatchService,
    personService: PersonService,
) {
    route(BASE_PATH_ADMIN) {
        post("bestillingsbatcher") {
            call.requireScope(requiredScope = Scope.ADMIN_SCOPE)
            val request = call.receive<BatchInsightRequest>()
            val bestillingsbatcher = bestillingsbatchService.getBestillingsbatches(request.tidspunktFom, request.tidspunktTom, request.status, request.type)
            call.respond(
                BatchInsightResponse(bestillingsbatcher),
            )
        }

        post("auditLogg") {
            call.requireScope(requiredScope = Scope.ADMIN_SCOPE)
            val request = call.receive<FnrRequest>()
            val audits = personService.getAuditLogs(request.fnr)
            call.respond(AuditResponse(audits))
        }
        patch("bestillingsbatcher/{id}") {
            call.requireScope(requiredScope = Scope.ADMIN_SCOPE)
            val bestillingsreferanse =
                Bestillingsreferanse(
                    call.parameters["id"]
                        ?: return@patch call.respond(HttpStatusCode.BadRequest, "Mangler Id"),
                )
            bestillingsbatchService.rerun(bestillingsreferanse)
            return@patch call.respond(HttpStatusCode.Accepted)
        }
    }
}
