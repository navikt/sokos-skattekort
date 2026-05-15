package no.nav.sokos.skattekort.api

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

import io.ktor.http.HttpStatusCode
import io.ktor.server.plugins.requestvalidation.RequestValidationException
import io.ktor.server.request.httpMethod
import io.ktor.server.request.receive
import io.ktor.server.request.uri
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import mu.KotlinLogging

import no.nav.sokos.skattekort.api.model.ForespoerselRequest
import no.nav.sokos.skattekort.api.model.StatusResponse
import no.nav.sokos.skattekort.config.TEAM_LOGS_MARKER
import no.nav.sokos.skattekort.forespoersel.ForespoerselService
import no.nav.sokos.skattekort.person.PersonService
import no.nav.sokos.skattekort.security.AuthorizationGuard.getNavIdentOrNull
import no.nav.sokos.skattekort.security.AuthorizationGuard.requirePermission
import no.nav.sokos.skattekort.security.AuthorizationGuard.requireScope
import no.nav.sokos.skattekort.security.Role
import no.nav.sokos.skattekort.security.Saksbehandler
import no.nav.sokos.skattekort.security.Scope
import no.nav.sokos.skattekort.skattekort.SkattekortValidator
import no.nav.sokos.skattekort.skattekortbestilling.status.StatusService

private val logger = KotlinLogging.logger { }

const val BASE_PATH_SKATTEKORT = "/api/v1/skattekort"

fun Route.skattekortApi(
    forespoerselService: ForespoerselService,
    personService: PersonService,
    statusService: StatusService,
) {
    route(BASE_PATH_SKATTEKORT) {
        post("bestille") {
            call.requirePermission(requiredScope = Scope.BESTILLE_SCOPE, requiredRole = Role.BESTILLE_ROLE)
            val request = call.receive<ForespoerselRequest>()
            val saksbehandler = call.getNavIdentOrNull()?.let { Saksbehandler(it) }

            logger.info(marker = TEAM_LOGS_MARKER) {
                "skattekortApi - method=${call.request.httpMethod.value} uri=${call.request.uri} - Mottatt forespørsel: $request på vegne av ${saksbehandler?.ident}"
            }

            val message = "${request.forsystem};${request.aar};${request.personIdent}"
            forespoerselService.taImotForespoersel(message, saksbehandler)
            call.respond(HttpStatusCode.Created)
        }

        post("bestillingbulk/{forsystem}/{inntektsaar}") {
            call.requireScope(Scope.ADMIN_SCOPE)

            val forsystem = call.parameters["forsystem"]
            val inntektsaar = call.parameters["inntektsaar"]
            val fnrSet =
                call
                    .receive<ByteArray>()
                    .toString(Charsets.UTF_8)
                    .lineSequence()
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .toSet()
                    .ifEmpty {
                        throw RequestValidationException(
                            value = "FNR",
                            reasons = listOf("Mangler FNR"),
                        )
                    }
            SkattekortValidator.validateBestillingBulkParams(forsystem, inntektsaar, fnrSet)

            logger.info(marker = TEAM_LOGS_MARKER) {
                "skattekortApi - method=${call.request.httpMethod.value} uri=${call.request.uri} - Mottatt forespørsel: $fnrSet"
            }

            val validFnrList = personService.validateFoedselsnummer(fnrSet.toList())
            val invalidFnrList = fnrSet.filterNot { it in validFnrList }.distinct()

            if (invalidFnrList.isNotEmpty()) {
                throw RequestValidationException(
                    value = fnrSet,
                    reasons = listOf("Ugyldige FNR funnet. Antall: ${invalidFnrList.size}. Verdier: $invalidFnrList"),
                )
            }

            call.respond(HttpStatusCode.Accepted)

            CoroutineScope(Dispatchers.IO).launch {
                fnrSet.forEach { fnr ->
                    val message = "$forsystem;$inntektsaar;$fnr"
                    forespoerselService.taImotForespoersel(message, saksbehandler = null)
                }
            }
        }

        post("status") {
            call.requireScope(Scope.STATUS_SCOPE)
            val request = call.receive<ForespoerselRequest>()
            val saksbehandler = call.getNavIdentOrNull()?.let { Saksbehandler(it) } ?: return@post call.respond(HttpStatusCode.Unauthorized)

            logger.info(marker = TEAM_LOGS_MARKER) {
                "skattekortApi - Mottatt forespørsel: $request på vegne av ${saksbehandler.ident}"
            }

            call.respond(
                StatusResponse(statusService.statusForespoeresel(request.personIdent, request.aar, request.forsystem, saksbehandler)),
            )
        }
    }
}
