package no.nav.sokos.skattekort.api.model

import kotlinx.serialization.Serializable

import io.ktor.server.plugins.requestvalidation.RequestValidationConfig
import io.ktor.server.plugins.requestvalidation.ValidationResult

import no.nav.sokos.skattekort.forespoersel.Forsystem
import no.nav.sokos.skattekort.skattekort.SkattekortValidator.allFnrAreValid
import no.nav.sokos.skattekort.skattekort.SkattekortValidator.isValidForsystem

@Serializable
data class UtsendingRequest(
    val fnr: List<String> = emptyList(),
    val aar: Int,
    val forsystem: String,
)

fun RequestValidationConfig.requestValidationUtsendingConfig() {
    validate<UtsendingRequest> { request ->
        when {
            request.fnr.isEmpty() -> {
                ValidationResult.Invalid("Listen av fnr kan ikke være tom. Den må inneholde minst ett fnr.")
            }

            !allFnrAreValid(request.fnr) -> {
                ValidationResult.Invalid("Minst en personIdent er ugyldig. Tillatt format er 11 siffer")
            }

            !isValidForsystem(request.forsystem) -> {
                ValidationResult.Invalid(
                    "Forsystem er ugyldig. Gyldige verdier er: ${Forsystem.entries.filterNot { it in listOf(Forsystem.OPPDRAGSSYSTEMET_STOR, Forsystem.MANUELL) }.joinToString { it.value }}",
                )
            }

            else -> {
                ValidationResult.Valid
            }
        }
    }
}
