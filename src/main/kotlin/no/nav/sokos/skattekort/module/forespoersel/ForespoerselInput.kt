package no.nav.sokos.skattekort.module.forespoersel

import java.time.LocalDateTime

data class ForespoerselInput(
    val forsystem: Forsystem,
    val inntektsaar: Int,
    val fnrList: List<String>,
) {
    fun isBatchProcess(): Boolean = fnrList.size > 1 && forsystem == Forsystem.OPPDRAGSSYSTEMET && inntektsaar == LocalDateTime.now().year + 1
}
