package no.nav.sokos.skattekort.api.model

import kotlinx.serialization.Serializable

import io.ktor.server.plugins.requestvalidation.RequestValidationConfig
import io.ktor.server.plugins.requestvalidation.ValidationResult
import io.ktor.server.plugins.requestvalidation.ValidationResult.Invalid

import no.nav.sokos.skattekort.skattekort.SkattekortValidator.isValidPersonIdent

@Serializable
data class HentNavnRequest(
    val fnr: String,
)

fun RequestValidationConfig.requestValidationHentNavnRequest() {
    validate<HentNavnRequest> { request ->
        when {
            !isValidPersonIdent(request.fnr) -> Invalid("fnr er ugyldig. Tillatt format er 11 siffer, var ${request.fnr}")
            else -> ValidationResult.Valid
        }
    }
}
