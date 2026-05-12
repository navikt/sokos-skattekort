package no.nav.sokos.skattekort.skattekort

import java.time.Year

import io.ktor.server.plugins.requestvalidation.RequestValidationException

import no.nav.sokos.skattekort.forespoersel.Forsystem

object SkattekortValidator {
    fun isValidPersonIdent(personIdent: String): Boolean = Regex("^\\d{11}$").matches(personIdent)

    fun allFnrAreValid(fnrs: List<String>): Boolean = fnrs.all { isValidPersonIdent(it) }

    fun isValidAar(aar: Int): Boolean {
        val currentYear = Year.now().value
        return aar in (currentYear - 1)..currentYear
    }

    fun isValidInntektsaar(aar: Int): Boolean = aar in ReglerForInntektsaar.alleLovligeInntektsaarAaHenteSkattekortFor()

    fun isValidForsystem(forsystem: String): Boolean {
        val gyldigForSystem = Forsystem.entries.filterNot { it == Forsystem.OPPDRAGSSYSTEMET_STOR }
        return !forsystem.isEmpty() && gyldigForSystem.any { it.value == forsystem }
    }

    fun validateBestillingBulkParams(
        forsystem: String?,
        inntektsaar: String?,
        fnrSet: Set<String>,
    ) {
        val reasons = mutableListOf<String>()

        if (forsystem.isNullOrBlank() || !isValidForsystem(forsystem)) {
            reasons += "forsystem er ugyldig. Gyldige verdier er: ${Forsystem.entries.filterNot { it == Forsystem.OPPDRAGSSYSTEMET_STOR }.joinToString { it.value }}"
        }

        val aar = inntektsaar?.toIntOrNull()
        if (aar == null || !isValidAar(aar)) {
            reasons += "inntektsår er ugyldig. Gyldig årstall er mellom ${Year.now().value - 1} og ${Year.now().value}"
        }

        if (fnrSet.isEmpty()) {
            reasons += "Mangler FNR"
        }

        if (reasons.isNotEmpty()) {
            throw RequestValidationException(value = "$forsystem/$inntektsaar", reasons = reasons)
        }
    }
}
