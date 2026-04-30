package no.nav.sokos.skattekort.forespoersel

data class ForespoerselInput(
    val forsystem: Forsystem,
    val inntektsaar: Int,
    val fnrList: List<String>,
)
