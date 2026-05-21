package no.nav.sokos.skattekort.forespoersel

private const val DELIMITER = ";"

data class ForespoerselInput(
    val forsystem: Forsystem,
    val inntektsaar: Int,
    val fnrList: List<String>,
) {
    fun getMessage() = "${forsystem.value}$DELIMITER$inntektsaar$DELIMITER${fnrList.joinToString(separator = DELIMITER)}"
}
