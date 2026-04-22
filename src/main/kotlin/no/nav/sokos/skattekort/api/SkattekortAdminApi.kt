package no.nav.sokos.skattekort.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route

import no.nav.sokos.skattekort.api.model.AuditResponse
import no.nav.sokos.skattekort.api.model.BatchInsightRequest
import no.nav.sokos.skattekort.api.model.BatchInsightResponse
import no.nav.sokos.skattekort.api.model.FnrRequest
import no.nav.sokos.skattekort.api.model.UtsendingRequest
import no.nav.sokos.skattekort.forespoersel.Forsystem
import no.nav.sokos.skattekort.person.PersonService
import no.nav.sokos.skattekort.person.Personidentifikator
import no.nav.sokos.skattekort.skattekortbestilling.BestillingsbatchService
import no.nav.sokos.skattekort.skattekortbestilling.Bestillingsreferanse
import no.nav.sokos.skattekort.utsending.UtsendingService

const val BASE_PATH_ADMIN = "/api/v1/admin"

fun Route.skattekortAdminApi(
    bestillingsbatchService: BestillingsbatchService,
    personService: PersonService,
    utsendingService: UtsendingService,
) {
    route(BASE_PATH_ADMIN) {
        post("hentBatcher") {
            // Utkommentert fordi vi ikke har deployet ennå
//            call.requireScope(requiredScope = Scope.ADMIN_SCOPE)
            val request = call.receive<BatchInsightRequest>()
            val bestillingsbatcher = bestillingsbatchService.getBestillingsbatches(request.tidspunktFom, request.tidspunktTom, request.status, request.type)
            call.respond(
                BatchInsightResponse(bestillingsbatcher),
            )
        }

        post("auditLogg") {
            // Utkommentert fordi vi ikke har deployet ennå
//            call.requireScope(requiredScope = Scope.ADMIN_SCOPE)
            val request = call.receive<FnrRequest>()
            val audits = personService.getAuditLogs(request.fnr)
            call.respond(AuditResponse(audits))
        }
        get("rerun/{bestillingsreferanse}") {
            //            call.requireScope(requiredScope = Scope.ADMIN_SCOPE)

            val bestillingsreferanse =
                Bestillingsreferanse(
                    call.parameters["bestillingsreferanse"] ?: return@get call.respond(HttpStatusCode.BadRequest, "Invalid Bestillingsreferanse: must be 2–3 letters followed by 4–8 digits"),
                )
            bestillingsbatchService.rerun(bestillingsreferanse)
            return@get call.respond(HttpStatusCode.Accepted)
        }

        post("utsendtStatus") {
            val request = call.receive<UtsendingRequest>()
            return@post call.respond(HttpStatusCode.NotImplemented)
        }

        post("utsending") {
//            call.requireScope(requiredScope = Scope.ADMIN_SCOPE)
            val request = call.receive<UtsendingRequest>()
            utsendingService.createUtsendingForMangeFnr(request.fnr.map { fnr -> Personidentifikator(fnr) }, request.aar, Forsystem.fromValue(request.forsystem))
            return@post call.respond(HttpStatusCode.Accepted)
        }
    }
}
