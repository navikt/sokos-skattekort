package no.nav.sokos.skattekort.api.model

import java.time.Year

import kotlinx.serialization.Serializable

import io.ktor.server.plugins.requestvalidation.RequestValidationConfig
import io.ktor.server.plugins.requestvalidation.ValidationResult

import no.nav.sokos.skattekort.module.forespoersel.Forsystem
import no.nav.sokos.skattekort.module.skattekort.SkattekortPersonValidator.isValidAar
import no.nav.sokos.skattekort.module.skattekort.SkattekortPersonValidator.isValidForsystem
import no.nav.sokos.skattekort.module.skattekort.SkattekortPersonValidator.isValidPersonIdent

@Serializable
data class ForespoerselRequest(
    val personIdent: String,
    val aar: Int,
    val forsystem: String,
)

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
