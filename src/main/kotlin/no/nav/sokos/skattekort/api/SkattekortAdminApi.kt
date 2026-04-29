package no.nav.sokos.skattekort.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.route

import no.nav.sokos.skattekort.api.model.AuditResponse
import no.nav.sokos.skattekort.api.model.BatchInsightRequest
import no.nav.sokos.skattekort.api.model.BatchInsightResponse
import no.nav.sokos.skattekort.api.model.BestillingDTO
import no.nav.sokos.skattekort.api.model.BestillingResponse
import no.nav.sokos.skattekort.api.model.FnrRequest
import no.nav.sokos.skattekort.api.model.UtsendingDTO
import no.nav.sokos.skattekort.api.model.UtsendingResponse
import no.nav.sokos.skattekort.dto.NoekkelinformasjonResponse
import no.nav.sokos.skattekort.person.PersonService
import no.nav.sokos.skattekort.security.AuthorizationGuard.requireScope
import no.nav.sokos.skattekort.security.Scope
import no.nav.sokos.skattekort.skattekort.SkattekortService
import no.nav.sokos.skattekort.skattekortbestilling.BestillingsbatchService
import no.nav.sokos.skattekort.utsending.UtsendingService

const val BASE_PATH_ADMIN = "/api/v1/admin"

fun Route.skattekortAdminApi(
    bestillingsbatchService: BestillingsbatchService,
    personService: PersonService,
    utsendingService: UtsendingService,
    skattekortService: SkattekortService,
) {
    route(BASE_PATH_ADMIN) {
        post("bestillingsbatcher") {
            call.requireScope(requiredScope = Scope.ADMIN_SCOPE)
            val request = call.receive<BatchInsightRequest>()
            val bestillingsbatcher = bestillingsbatchService.getBestillingsbatches(request.tidspunktFom, request.tidspunktTom)
            call.respond(
                BatchInsightResponse(bestillingsbatcher),
            )
        }
        get("bestillingsbatcher") {
            call.requireScope(requiredScope = Scope.ADMIN_SCOPE)
            val bestillingsbatcher = bestillingsbatchService.getIncompleteBestillingsbatchesWithoutJson()
            call.respond(
                BatchInsightResponse(bestillingsbatcher),
            )
        }
        get("bestillinger") {
            call.requireScope(requiredScope = Scope.ADMIN_SCOPE)
            val bestillingsbatcher = bestillingsbatchService.getAllBestillings()
            call.respond(
                BestillingResponse(bestillingsbatcher.map(BestillingDTO::fromDomain)),
            )
        }
        get("utsendinger") {
            call.requireScope(requiredScope = Scope.ADMIN_SCOPE)
            val utsendinger = utsendingService.getAllUtsendinger()
            call.respond(
                UtsendingResponse(utsendinger.map(UtsendingDTO::fromDomain)),
            )
        }

        get("noekkelinformasjon") {
            call.requireScope(requiredScope = Scope.ADMIN_SCOPE)
            call.respond(
                NoekkelinformasjonResponse(skattekortService.getNoekkelinformasjon()),
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
            val id = call.parameters["id"]?.toLongOrNull() ?: return@patch call.respond(HttpStatusCode.BadRequest, "Invalid id")
            val updatedRows = bestillingsbatchService.rerun(id)
            return@patch call.respond(HttpStatusCode.Accepted, "Oppdaterte $updatedRows batcher")
        }
    }
}
