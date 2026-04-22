package no.nav.sokos.skattekort.skattekort

import java.time.Year

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
}
