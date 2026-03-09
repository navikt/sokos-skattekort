package no.nav.sokos.skattekort.api.model

import kotlinx.serialization.Serializable

import io.ktor.server.plugins.requestvalidation.RequestValidationConfig
import io.ktor.server.plugins.requestvalidation.ValidationResult
import io.ktor.server.plugins.requestvalidation.ValidationResult.Invalid

import no.nav.sokos.skattekort.dto.SkattekortDTO
import no.nav.sokos.skattekort.dto.validTilleggsopplysningList
import no.nav.sokos.skattekort.dto.validTrekkodeList
import no.nav.sokos.skattekort.skattekort.ResultatForSkattekort
import no.nav.sokos.skattekort.skattekort.SkattekortValidator.isValidPersonIdent

@Serializable
data class OpprettSkattekortRequest(
    val fnr: String,
    val skattekort: SkattekortDTO,
)

fun RequestValidationConfig.requestValidationOpprettSkattekortRequest() {
    validate<OpprettSkattekortRequest> { request ->
        when {
            !isValidPersonIdent(request.fnr) -> Invalid("fnr er ugyldig. Tillatt format er 11 siffer, var ${request.fnr}")
            request.invalidResultatForSkattekort() -> Invalid("Ugyldig ResultatForSkattekort, lovlige verdier er: ${ResultatForSkattekort.entries.joinToString { it.value }} .")
            request.hasInvalidTrekkode() -> Invalid("Ugyldige trekkode. Lovlige verdier er ${validTrekkodeList.joinToString { it.value }}.")
            request.hasInvalidTilleggsopplysning() -> Invalid("Ugyldig tilleggsopplysning. Lovlige verdier er ${validTilleggsopplysningList.joinToString()}.")

            else -> ValidationResult.Valid
        }
    }
}

fun OpprettSkattekortRequest.invalidResultatForSkattekort(): Boolean =
    runCatching {
        this.skattekort.resultatForSkattekort?.let(ResultatForSkattekort::fromValue) == null
    }.getOrDefault(true)

fun OpprettSkattekortRequest.hasInvalidTrekkode(): Boolean =
    runCatching {
        this.skattekort.forskuddstrekkList
            .asSequence()
            .map { it.toDomainForskuddstrekk() }
            .map { it.trekkode() }
            .any { it !in validTrekkodeList }
    }.getOrDefault(true)

fun OpprettSkattekortRequest.hasInvalidTilleggsopplysning(): Boolean =
    runCatching {
        this.skattekort.tilleggsopplysningList.any { it !in validTilleggsopplysningList }
    }.getOrDefault(true)
