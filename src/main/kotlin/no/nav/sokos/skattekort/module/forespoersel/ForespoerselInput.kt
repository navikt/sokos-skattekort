package no.nav.sokos.skattekort.module.forespoersel

data class ForespoerselInput(
    val forsystem: Forsystem,
    val inntektsaar: Int,
    val fnrList: List<String>,
)
