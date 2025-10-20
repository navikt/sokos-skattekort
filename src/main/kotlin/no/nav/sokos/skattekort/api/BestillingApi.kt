package no.nav.sokos.skattekort.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import mu.KotlinLogging

import no.nav.sokos.skattekort.config.TEAM_LOGS_MARKER
import no.nav.sokos.skattekort.module.skattekort.BestillingsService

private val logger = KotlinLogging.logger { }

fun Route.bestillingApi(service: BestillingsService) {
    route("api/v1") {
        get("sendBestilling") {
            logger.info(marker = TEAM_LOGS_MARKER) { "foerespoerselApi - Skal sende bestillingsbatch" }
            service.opprettBestillingsbatch()
            call.respond(HttpStatusCode.OK)
        }
    }
}
