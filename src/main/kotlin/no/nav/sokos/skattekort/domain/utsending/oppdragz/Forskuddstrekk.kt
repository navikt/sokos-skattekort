package no.nav.sokos.skattekort.domain.utsending.oppdragz

import java.math.BigDecimal

abstract class Forskuddstrekk(
    open val trekkode: Trekkode,
)

data class Frikort(
    override val trekkode: Trekkode,
    val frikortbeloep: BigDecimal? = null,
) : Forskuddstrekk(trekkode)

data class Trekkprosent(
    override val trekkode: Trekkode,
    val trekkprosent: BigDecimal,
    val antallMaanederForTrekk: BigDecimal? = null,
) : Forskuddstrekk(trekkode)

data class Trekktabell(
    override val trekkode: Trekkode,
    val tabelltype: Tabelltype,
    val tabellnummer: String,
    val prosentsats: BigDecimal,
    val antallMaanederForTrekk: BigDecimal? = null,
) : Forskuddstrekk(trekkode)

enum class Tabelltype(
    val value: String,
) {
    TREKKTABELL_FOR_PENSJON("trekktabellForPensjon"),
    TREKKTABELL_FOR_LOENN("trekktabellForLoenn"),
    ;

    companion object {
        fun fromValue(value: String): Tabelltype = entries.first { it.value == value }
    }
}
