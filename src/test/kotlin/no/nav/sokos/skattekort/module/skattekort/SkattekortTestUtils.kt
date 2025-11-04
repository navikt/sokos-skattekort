package no.nav.sokos.skattekort.module.skattekort

import java.math.BigDecimal
import java.math.RoundingMode

fun aForskuddstrekk(
    type: String,
    trekkode: Trekkode,
    prosentSats: Double,
    antMndForTrekk: Double? = null,
    tabellNummer: String? = null,
): Forskuddstrekk =
    when (type) {
        Prosentkort::class.simpleName ->
            Prosentkort(
                trekkode.value,
                BigDecimal(prosentSats).setScale(2, RoundingMode.HALF_UP),
                antMndForTrekk?.let { belop -> BigDecimal(belop).setScale(1, RoundingMode.HALF_UP) },
            )

        Tabellkort::class.simpleName ->
            Tabellkort(
                trekkode.value,
                tabellNummer!!,
                BigDecimal(prosentSats).setScale(2, RoundingMode.HALF_UP),
                BigDecimal(antMndForTrekk ?: 12.0).setScale(1, RoundingMode.HALF_UP),
            )

        else -> error("Ukjent forskuddstrekk-type: $type")
    }
