package no.nav.sokos.skattekort.config

import java.time.Year

import io.ktor.server.plugins.requestvalidation.RequestValidationConfig
import io.ktor.server.plugins.requestvalidation.ValidationResult

import no.nav.sokos.skattekort.api.ForespoerselRequest
import no.nav.sokos.skattekort.api.skattekortpersonapi.v1.SkattekortPersonRequest
import no.nav.sokos.skattekort.config.Validator.isValidAar
import no.nav.sokos.skattekort.config.Validator.isValidForsystem
import no.nav.sokos.skattekort.config.Validator.isValidPersonIdent
import no.nav.sokos.skattekort.module.forespoersel.Forsystem

fun RequestValidationConfig.requestValidationSkattekortConfig() {
    validate<ForespoerselRequest> { request ->
        when {
            !isValidPersonIdent(request.personIdent) -> ValidationResult.Invalid("personIdent er ugyldig. Tillatt format er 11 siffer")
            !isValidAar(request.aar) -> ValidationResult.Invalid("Gyldig årstall er mellom ${Year.now().minusYears(1)} og inneværende år")
            !isValidForsystem(request.forsystem) ->
                ValidationResult.Invalid("forsystem er ugyldig. Gyldige verdier er: ${Forsystem.entries.filterNot { it == Forsystem.OPPDRAGSSYSTEMET_STOR }.joinToString { it.value }}")

            else -> ValidationResult.Valid
        }
    }
}

fun RequestValidationConfig.requestValidationSkattekortRequest() {
    validate<SkattekortPersonRequest> { request ->
        when {
            !isValidPersonIdent(request.fnr) -> ValidationResult.Invalid("fnr er ugyldig. Tillatt format er 11 siffer, var ${request.fnr}")
            !Validator.isValidInntektsaar(request.inntektsaar.toInt()) -> ValidationResult.Invalid("inntektsaar ser ikke ut som et gyldig årstall, var ${request.inntektsaar}")
            else -> ValidationResult.Valid
        }
    }
}

object Validator {
    fun isValidPersonIdent(personIdent: String): Boolean = Regex("^\\d{11}$").matches(personIdent)

    fun isValidAar(aar: Int): Boolean {
        val currentYear = Year.now().value
        return aar in (currentYear - 1)..currentYear
    }

    fun isValidInntektsaar(aar: Int): Boolean = aar in 2025..<2100

    fun isValidForsystem(forsystem: String): Boolean {
        val gyldigForSystem = Forsystem.entries.filterNot { it == Forsystem.OPPDRAGSSYSTEMET_STOR }
        return !forsystem.isEmpty() && gyldigForSystem.any { it.value == forsystem }
    }
}
