package no.nav.sokos.skattekort.api

import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import mu.KotlinLogging

import no.nav.sokos.skattekort.api.model.BatchInsightRequest
import no.nav.sokos.skattekort.api.model.BatchInsightResponse
import no.nav.sokos.skattekort.skattekortbestilling.BestillingsbatchService

private val logger = KotlinLogging.logger { }

const val BASE_PATH_ADMIN = "/api/v1/admin"

fun Route.skattekortAdminApi(bestillingsbatchService: BestillingsbatchService) {
    route(BASE_PATH_ADMIN) {
        post("hentBatcher") {
            // Utkommentert fordi vi ikke har deployet ennå
//            call.requireScope(requiredScope = Scope.ADMIN_SCOPE)
            val request = call.receive<BatchInsightRequest>()
            logger.info("Fra: ${request.tidspunktFom}, Til: ${request.tidspunktTom}")
            val bestillingsbatcher = bestillingsbatchService.getBestillingsbatches(request.tidspunktFom, request.tidspunktTom)
            call.respond(
                BatchInsightResponse(bestillingsbatcher),
            )
        }
    }
}
