package no.nav.sokos.skattekort.skattekort

import java.math.BigDecimal

import kotlin.time.ExperimentalTime

object Syntetisering {
    @OptIn(ExperimentalTime::class)
    fun evtSyntetiserSkattekort(
        skattekort: Skattekort,
        id: SkattekortId,
    ): Pair<Skattekort, String>? =
        genererForskuddstrekk(skattekort)?.let { (syntetiserteForskuddstrekk, aarsak) ->
            Pair(
                Skattekort(
                    id = null,
                    personId = skattekort.personId,
                    utstedtDato = null,
                    identifikator = null,
                    inntektsaar = skattekort.inntektsaar,
                    kilde = SkattekortKilde.SYNTETISERT.value,
                    resultatForSkattekort = skattekort.resultatForSkattekort,
                    opprettet = skattekort.opprettet,
                    tilleggsopplysningList = skattekort.tilleggsopplysningList,
                    forskuddstrekkList = syntetiserteForskuddstrekk,
                    generertFra = id,
                ),
                aarsak,
            )
        }

    private fun genererForskuddstrekk(skattekort: Skattekort): Pair<List<Forskuddstrekk>, String>? {
        val manglendeKildeskattPaaPensjonSatser = manglendeKildeskattPaaPensjonSatser(skattekort.forskuddstrekkList)
        return when {
            skattekort.resultatForSkattekort == ResultatForSkattekort.IkkeTrekkplikt -> {
                Pair(
                    listOf(Trekkode.LOENN_FRA_NAV, Trekkode.PENSJON_FRA_NAV, Trekkode.UFOERETRYGD_FRA_NAV)
                        .map { Frikort(it, null) },
                    "Frikort uten beløpsgrense syntetisert fordi brukeren ikke er trekkpliktig",
                )
            }

            skattekort.tilleggsopplysningList.contains(Tilleggsopplysning.OPPHOLD_PAA_SVALBARD) -> {
                Pair(
                    svalbardsatser(skattekort.inntektsaar),
                    "Prosentkort med default skattesatser for Svalbard syntetisert pga mottatt tilleggsinformasjon ${Tilleggsopplysning.OPPHOLD_PAA_SVALBARD.value}",
                )
            }

            skattekort.tilleggsopplysningList.contains(Tilleggsopplysning.KILDESKATT_PAA_PENSJON) &&
                manglendeKildeskattPaaPensjonSatser.isNotEmpty() -> {
                Pair(
                    (manglendeKildeskattPaaPensjonSatser + skattekort.forskuddstrekkList).sortedBy { it.trekkode() },
                    """Skattekort syntetisert med manglende trekkoder ${
                        manglendeKildeskattPaaPensjonSatser.joinToString(
                            ",",
                        )
                    } pga mottatt tilleggsinformasjon ${Tilleggsopplysning.KILDESKATT_PAA_PENSJON}""",
                )
            }

            else -> null
        }
    }
}

fun svalbardsatser(inntektsaar: Int): List<Forskuddstrekk> =
    when (inntektsaar) {
        2025 -> listOf(15.70, 13.10, 15.70)
        2026 -> listOf(15.60, 13.10, 15.60)
        else -> error("Har ikke svalbardsatser for: $inntektsaar")
    }.map(Double::toBigDecimal)
        .zip(listOf(Trekkode.LOENN_FRA_NAV, Trekkode.PENSJON_FRA_NAV, Trekkode.UFOERETRYGD_FRA_NAV)) { prosentsats, trekkode ->
            Prosentkort(trekkode, prosentsats)
        }

fun manglendeKildeskattPaaPensjonSatser(fraSkd: List<Forskuddstrekk>): List<Forskuddstrekk> =
    listOf(Trekkode.PENSJON_FRA_NAV, Trekkode.UFOERETRYGD_FRA_NAV)
        .subtract(fraSkd.map { it.trekkode() })
        .map {
            Prosentkort(
                trekkode = it,
                prosentSats = BigDecimal.valueOf(15.00),
            )
        }
