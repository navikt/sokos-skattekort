package no.nav.sokos.skattekort.module.skattekort

import java.time.Year

import no.nav.sokos.skattekort.module.forespoersel.Forsystem

object SkattekortPersonValidator {
    fun isValidPersonIdent(personIdent: String): Boolean = Regex("^\\d{11}$").matches(personIdent)

    fun isValidAar(aar: Int): Boolean {
        val currentYear = Year.now().value
        return aar in (currentYear - 1)..currentYear
    }

    fun isValidInntektsaar(aar: Short): Boolean = aar in ReglerForInntektsaar.alleLovligeInntektsaarAaHenteSkattekortFor()

    fun isValidForsystem(forsystem: String): Boolean {
        val gyldigForSystem = Forsystem.entries.filterNot { it == Forsystem.OPPDRAGSSYSTEMET_STOR }
        return !forsystem.isEmpty() && gyldigForSystem.any { it.value == forsystem }
    }
}
