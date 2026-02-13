package no.nav.sokos.skattekort.api

import java.time.Year

import kotlinx.serialization.Serializable

import io.ktor.http.HttpStatusCode
import io.ktor.server.plugins.requestvalidation.RequestValidationConfig
import io.ktor.server.plugins.requestvalidation.ValidationResult
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route

import no.nav.sokos.skattekort.api.skattekortpersonapi.v1.SkattekortPersonRequest
import no.nav.sokos.skattekort.config.PropertiesConfig
import no.nav.sokos.skattekort.dto.SkattekortDTO
import no.nav.sokos.skattekort.module.forespoersel.Forsystem
import no.nav.sokos.skattekort.module.skattekort.ResultatForSkattekort
import no.nav.sokos.skattekort.module.skattekort.SkattekortPersonService
import no.nav.sokos.skattekort.module.skattekort.SkattekortPersonValidator
import no.nav.sokos.skattekort.module.skattekort.SkattekortPersonValidator.isValidAar
import no.nav.sokos.skattekort.module.skattekort.SkattekortPersonValidator.isValidForsystem
import no.nav.sokos.skattekort.module.skattekort.SkattekortPersonValidator.isValidPersonIdent
import no.nav.sokos.skattekort.security.AuthorizationGuard.getNavIdentOrNull
import no.nav.sokos.skattekort.security.AuthorizationGuard.requireScopeOrRole
import no.nav.sokos.skattekort.security.Role
import no.nav.sokos.skattekort.security.Saksbehandler
import no.nav.sokos.skattekort.security.Scope

fun Route.skattekortPersonApi(skattekortPersonService: SkattekortPersonService) {
    route("/api/v1/person") {
        post("hent-skattekort") {
            call.requireScopeOrRole(Scope.BASIC_READ.value)
            val skattekortPersonRequest: SkattekortPersonRequest = call.receive()
            val saksbehandler = call.getNavIdentOrNull()?.let { Saksbehandler(it) }
            call.respond(
                skattekortPersonService.hentSkattekortPerson(skattekortPersonRequest.fnr, skattekortPersonRequest.inntektsaar, saksbehandler),
            )
        }
        post("sjekk") {
            call.requireScopeOrRole(Scope.BASIC_READ.value)
            if (PropertiesConfig.getApplicationProperties().environment != PropertiesConfig.Environment.PROD) {
                val skattekortPersonRequest: SkattekortPersonRequest = call.receive()
                val saksbehandler = call.getNavIdentOrNull()?.let { Saksbehandler(it) }
                call.respond(
                    skattekortPersonService
                        .hentSkattekortPerson(
                            skattekortPersonRequest.fnr,
                            skattekortPersonRequest.inntektsaar,
                            saksbehandler,
                        ).isNotEmpty(),
                )
            } else {
                call.respond(HttpStatusCode.NotAcceptable)
            }
        }

        post("opprett") {
            call.requireScopeOrRole(Role.SKATTEKORT_WRITE.value)
            val request = call.receive<OpprettSkattekortRequest>()
            val saksbehandler = call.getNavIdentOrNull()?.let { Saksbehandler(it) }
            val id =
                skattekortPersonService.opprettSkattekort(
                    request.fnr,
                    request.skattekort,
                    saksbehandler,
                )
            call.respond(HttpStatusCode.Created)
        }
    }
}

fun Route.deprecatedSkattekortPersonApi(skattekortPersonService: SkattekortPersonService) {
    route("/api/v1") {
        post("hent-skattekort") {
            call.requireScopeOrRole(Scope.BASIC_READ.value)
            val skattekortPersonRequest: SkattekortPersonRequest = call.receive()
            val saksbehandler = call.getNavIdentOrNull()?.let { Saksbehandler(it) }
            call.respond(
                skattekortPersonService.hentSkattekortPerson(skattekortPersonRequest.fnr, skattekortPersonRequest.inntektsaar, saksbehandler),
            )
        }
    }
}

fun RequestValidationConfig.requestValidationSkattekortConfig() {
    validate<ForespoerselRequest> { request ->
        when {
            !isValidPersonIdent(request.personIdent) -> {
                ValidationResult.Invalid("personIdent er ugyldig. Tillatt format er 11 siffer")
            }

            !isValidAar(request.aar) -> {
                ValidationResult.Invalid("Gyldig årstall er mellom ${Year.now().minusYears(1)} og inneværende år")
            }

            !isValidForsystem(request.forsystem) -> {
                ValidationResult.Invalid("forsystem er ugyldig. Gyldige verdier er: ${Forsystem.entries.filterNot { it == Forsystem.OPPDRAGSSYSTEMET_STOR }.joinToString { it.value }}")
            }

            else -> {
                ValidationResult.Valid
            }
        }
    }
}

fun RequestValidationConfig.requestValidationSkattekortRequest() {
    validate<SkattekortPersonRequest> { request ->
        when {
            !isValidPersonIdent(request.fnr) -> ValidationResult.Invalid("fnr er ugyldig. Tillatt format er 11 siffer, var ${request.fnr}")

            request.inntektsaar != null &&
                !SkattekortPersonValidator.isValidInntektsaar(request.inntektsaar) -> ValidationResult.Invalid("inntektsaar ser ikke ut som et gyldig årstall, var ${request.inntektsaar}")

            else -> ValidationResult.Valid
        }
    }
}

fun RequestValidationConfig.requestValidationOpprettSkattekortRequest() {
    validate<OpprettSkattekortRequest> { request ->
        when {
            !isValidPersonIdent(request.fnr) -> ValidationResult.Invalid("fnr er ugyldig. Tillatt format er 11 siffer, var ${request.fnr}")

            try {
                request.skattekort.resultatForSkattekort?.let(ResultatForSkattekort::fromValue) != null
                request.skattekort.forskuddstrekkList.forEach { it.toDomainForskuddstrekk() }
                false
            } catch (e: Exception) {
                true
            } -> ValidationResult.Invalid("ugyldige verdier i skattekort-json.")

            else -> ValidationResult.Valid
        }
    }
}

@Serializable
data class OpprettSkattekortRequest(
    val fnr: String,
    val skattekort: SkattekortDTO,
)
