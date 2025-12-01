package no.nav.sokos.skattekort.module.skattekort

import java.math.BigDecimal

import kotlin.time.ExperimentalTime

object Syntetisering {
    @OptIn(ExperimentalTime::class)
    fun evtSyntetiserSkattekort(skattekort: Skattekort): Skattekort? {
        val syntetiserteForskuddstrekk =
            genererForskuddstrekk(skattekort.resultatForSkattekort, skattekort.tilleggsopplysningList)

        return syntetiserteForskuddstrekk?.let {
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
                generertFra = skattekort.id,
            )
        }
    }

    private fun genererForskuddstrekk(
        resultatForSkattekort: ResultatForSkattekort,
        tilleggsopplysning: List<Tilleggsopplysning>,
    ): List<Forskuddstrekk>? =
        when {
            resultatForSkattekort == ResultatForSkattekort.IkkeTrekkplikt -> {
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
                )
            }

            tilleggsopplysning.contains(Tilleggsopplysning.OPPHOLD_PAA_SVALBARD) -> {
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
                        prosentSats = BigDecimal.valueOf(13.00),
                    ),
                )
            }

            tilleggsopplysning.contains(Tilleggsopplysning.KILDESKATT_PAA_PENSJON) -> {
                listOf<Forskuddstrekk>(
                    Prosentkort(
                        trekkode = Trekkode.PENSJON_FRA_NAV,
                        prosentSats = BigDecimal.valueOf(15.00),
                    ),
                )
            }

            else -> {
                null
            }
        }
}
