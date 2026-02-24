package no.nav.sokos.skattekort.api.model

import kotlinx.serialization.Serializable

import io.ktor.server.plugins.requestvalidation.RequestValidationConfig
import io.ktor.server.plugins.requestvalidation.ValidationResult
import io.ktor.server.plugins.requestvalidation.ValidationResult.Invalid

import no.nav.sokos.skattekort.skattekort.SkattekortValidator.isValidInntektsaar
import no.nav.sokos.skattekort.skattekort.SkattekortValidator.isValidPersonIdent

@Serializable
data class HentSkattekortRequest(
    val fnr: String,
    val inntektsaar: Short? = null,
    val hentAlle: Boolean = false,
)

fun RequestValidationConfig.requestValidationSkattekortRequest() {
    validate<HentSkattekortRequest> { request ->
        when {
            !isValidPersonIdent(request.fnr) -> Invalid("fnr er ugyldig. Tillatt format er 11 siffer, var ${request.fnr}")
            request.inntektsaar != null && !isValidInntektsaar(request.inntektsaar) -> Invalid("inntektsaar ser ikke ut som et gyldig årstall, var ${request.inntektsaar}")
            else -> ValidationResult.Valid
        }
    }
}
