package no.nav.sokos.skattekort.api.model

import kotlinx.serialization.Serializable

import io.ktor.server.plugins.requestvalidation.RequestValidationConfig
import io.ktor.server.plugins.requestvalidation.ValidationResult

import no.nav.sokos.skattekort.dto.SkattekortDTO
import no.nav.sokos.skattekort.dto.validTilleggsopplysningList
import no.nav.sokos.skattekort.dto.validTrekkodeList
import no.nav.sokos.skattekort.module.skattekort.ResultatForSkattekort
import no.nav.sokos.skattekort.module.skattekort.SkattekortPersonValidator.isValidPersonIdent

@Serializable
data class OpprettSkattekortRequest(
    val fnr: String,
    val skattekort: SkattekortDTO,
)

fun RequestValidationConfig.requestValidationOpprettSkattekortRequest() {
    validate<OpprettSkattekortRequest> { request ->
        when {
            !isValidPersonIdent(request.fnr) -> ValidationResult.Invalid("fnr er ugyldig. Tillatt format er 11 siffer, var ${request.fnr}")
            try {
                request.skattekort.resultatForSkattekort?.let(ResultatForSkattekort::fromValue) == null
            } catch (e: Exception) {
                true
            } -> ValidationResult.Invalid("Ugyldig ResultatForSkattekort, lovlige verdier er: ${ResultatForSkattekort.entries.joinToString { it.value }} .")

            try {
                request.skattekort.forskuddstrekkList
                    .map { it.toDomainForskuddstrekk() }
                    .map { it.trekkode() }
                    .any { trekkode -> trekkode !in validTrekkodeList }
            } catch (e: Exception) {
                true
            } -> ValidationResult.Invalid("Ugyldige trekkode. Lovlige verdier er ${validTrekkodeList.joinToString { it.value }}.")

            try {
                request.skattekort.tilleggsopplysningList.any { opplysning -> opplysning !in validTilleggsopplysningList }
            } catch (e: Exception) {
                true
            } -> ValidationResult.Invalid("Ugyldig tilleggsopplysning. Lovlige verdier er ${validTilleggsopplysningList.joinToString()}.")

            else -> ValidationResult.Valid
        }
    }
}
