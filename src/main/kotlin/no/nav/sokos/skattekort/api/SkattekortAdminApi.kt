package no.nav.sokos.skattekort.api

import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import mu.KotlinLogging

import no.nav.sokos.skattekort.api.model.AuditResponse
import no.nav.sokos.skattekort.api.model.BatchInsightRequest
import no.nav.sokos.skattekort.api.model.BatchInsightResponse
import no.nav.sokos.skattekort.api.model.FnrRequest
import no.nav.sokos.skattekort.person.PersonService
import no.nav.sokos.skattekort.skattekortbestilling.BestillingsbatchService

private val logger = KotlinLogging.logger { }

const val BASE_PATH_ADMIN = "/api/v1/admin"

fun Route.skattekortAdminApi(
    bestillingsbatchService: BestillingsbatchService,
    personService: PersonService,
) {
    route(BASE_PATH_ADMIN) {
        post("hentBatcher") {
            // Utkommentert fordi vi ikke har deployet ennå
//            call.requireScope(requiredScope = Scope.ADMIN_SCOPE)
            val request = call.receive<BatchInsightRequest>()
            val bestillingsbatcher = bestillingsbatchService.getBestillingsbatches(request.tidspunktFom, request.tidspunktTom)
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
    }
}
