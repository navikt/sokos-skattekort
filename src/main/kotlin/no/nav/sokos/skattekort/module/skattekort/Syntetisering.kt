package no.nav.sokos.skattekort.module.skattekort

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

    private fun genererForskuddstrekk(skattekort: Skattekort): Pair<List<Forskuddstrekk>, String>? =
        when {
            skattekort.resultatForSkattekort == ResultatForSkattekort.IkkeTrekkplikt -> {
                Pair(
                    listOf<Forskuddstrekk>(
                        Frikort(
                            trekkode = Trekkode.LOENN_FRA_NAV,
                            frikortBeloep = null,
                        ),
                        Frikort(
                            trekkode = Trekkode.PENSJON_FRA_NAV,
                            frikortBeloep = null,
                        ),
                        Frikort(
                            trekkode = Trekkode.UFOERETRYGD_FRA_NAV,
                            frikortBeloep = null,
                        ),
                    ),
                    "Frikort uten beløpsgrense syntetisert fordi brukeren ikke er trekkpliktig",
                )
            }

            skattekort.tilleggsopplysningList.contains(Tilleggsopplysning.OPPHOLD_PAA_SVALBARD) -> {
                Pair(
                    listOf<Forskuddstrekk>(
                        Prosentkort(
                            trekkode = Trekkode.LOENN_FRA_NAV,
                            prosentSats = BigDecimal.valueOf(15.70),
                        ),
                        Prosentkort(
                            trekkode = Trekkode.UFOERETRYGD_FRA_NAV,
                            prosentSats = BigDecimal.valueOf(15.70),
                        ),
                        Prosentkort(
                            trekkode = Trekkode.PENSJON_FRA_NAV,
                            prosentSats = BigDecimal.valueOf(13.10),
                        ),
                    ),
                    "Prosentkort med default skattesatser for Svalbard syntetisert pga mottatt tilleggsinformasjon ${Tilleggsopplysning.OPPHOLD_PAA_SVALBARD.value}",
                )
            }

            skattekort.tilleggsopplysningList.contains(Tilleggsopplysning.KILDESKATT_PAA_PENSJON) -> {
                var forskuddstrekk = skattekort.forskuddstrekkList.toMutableList()
                var oppdatert = emptyList<String>().toMutableList()
                if (forskuddstrekk.find { it.trekkode() == Trekkode.PENSJON_FRA_NAV } == null) {
                    forskuddstrekk.add(
                        Prosentkort(
                            trekkode = Trekkode.PENSJON_FRA_NAV,
                            prosentSats = BigDecimal.valueOf(15.00),
                        ),
                    )
                    oppdatert.add(
                        Trekkode.PENSJON_FRA_NAV.value,
                    )
                }
                if (forskuddstrekk.find { it.trekkode() == Trekkode.UFOERETRYGD_FRA_NAV } == null) {
                    forskuddstrekk.add(
                        Prosentkort(
                            trekkode = Trekkode.UFOERETRYGD_FRA_NAV,
                            prosentSats = BigDecimal.valueOf(15.00),
                        ),
                    )
                    oppdatert.add(
                        Trekkode.UFOERETRYGD_FRA_NAV.value,
                    )
                }
                if (oppdatert.isNotEmpty()) {
                    Pair(
                        forskuddstrekk,
                        """Skattekort syntetisert med manglende trekkoder ${oppdatert.joinToString(",")} pga mottatt tilleggsinformasjon ${Tilleggsopplysning.KILDESKATT_PAA_PENSJON}""",
                    )
                } else {
                    null
                }
            }

            else -> {
                null
            }
        }
}
