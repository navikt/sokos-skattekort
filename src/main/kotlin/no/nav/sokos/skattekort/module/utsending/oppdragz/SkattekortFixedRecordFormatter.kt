package no.nav.sokos.skattekort.module.utsending.oppdragz

import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols

import no.nav.sokos.skattekort.module.skattekort.Forskuddstrekk
import no.nav.sokos.skattekort.module.skattekort.Frikort
import no.nav.sokos.skattekort.module.skattekort.Prosentkort
import no.nav.sokos.skattekort.module.skattekort.ResultatForSkattekort
import no.nav.sokos.skattekort.module.skattekort.Skattekort
import no.nav.sokos.skattekort.module.skattekort.Tabellkort
import no.nav.sokos.skattekort.module.skattekort.Tilleggsopplysning
import no.nav.sokos.skattekort.module.skattekort.Trekkode

class SkattekortFixedRecordFormatter internal constructor(
    private val skattekort: Skattekort,
    private val fnr: String,
) {
    private fun gyldigeForskuddstrekk(): List<Forskuddstrekk> {
        if (skattekort.resultatForSkattekort == ResultatForSkattekort.IkkeTrekkplikt) {
            return listOf(
                Frikort(Trekkode.LOENN_FRA_NAV, null),
                Frikort(Trekkode.PENSJON_FRA_NAV, null),
                Frikort(Trekkode.UFOERETRYGD_FRA_NAV, null),
            )
        } else {
            return skattekort
                .forskuddstrekkList
                .mapNotNull { t ->
                    when (t.trekkode()) {
                        Trekkode.LOENN_FRA_NAV -> t
                        Trekkode.PENSJON_FRA_NAV -> t
                        Trekkode.UFOERETRYGD_FRA_NAV -> t
                        else -> null
                    }
                }
        }
    }

    fun format(): String {
        val frSkattekort = StringBuilder()
        if (gyldigeForskuddstrekk().isNotEmpty()) {
            frSkattekort
                .append(fnr.padEnd(11))
                .append(formaterResultatPaaForesporsel())
                .append(skattekort.inntektsaar.toString().padEnd(4))
                .append(formaterUtstedtDato())
                .append(formaterSkattekortidentifikator())
                .append(formaterTilleggsopplysning())
                .append(formaterAntallSkattekortMedIMelding())
                .append(formaterForskuddstrekk())
        }
        return frSkattekort.toString()
    }

    private fun formaterResultatPaaForesporsel(): String =
        // Maks 40 posisjoner i fixedfield format til OS
        if (skattekort.resultatForSkattekort.value.length > 40) {
            skattekort.resultatForSkattekort.value.substring(0, 40)
        } else {
            skattekort.resultatForSkattekort.value.padEnd(40)
        }

    private fun formaterUtstedtDato(): String =
        if (skattekort.resultatForSkattekort == ResultatForSkattekort.IkkeTrekkplikt) {
            (skattekort.inntektsaar.toString() + UTSTEDT_DATO_IKKE_SKATTEPLIKT_POSTFIX).padEnd(10)
        } else if (ResultatForSkattekort.IkkeSkattekort.equals(skattekort.resultatForSkattekort)) {
            "".padEnd(10, ' ')
        } else {
            (skattekort.utstedtDato?.toString() ?: "").padEnd(10)
        }

    private fun formaterSkattekortidentifikator(): String =
        (
            if (skattekort.resultatForSkattekort == ResultatForSkattekort.IkkeTrekkplikt || skattekort.resultatForSkattekort == ResultatForSkattekort.IkkeSkattekort) {
                ""
            } else {
                skattekort.identifikator ?: ""
            }
        ).padEnd(10)

    private fun formaterTilleggsopplysning(): String = filterTilleggsopplysning(skattekort.tilleggsopplysningList).padEnd(50)

    private fun filterTilleggsopplysning(tilleggsopplysninger: List<Tilleggsopplysning>): String {
        val filtered =
            tilleggsopplysninger.mapNotNull {
                when (it) {
                    // Det er ikke noe overlapp her.
                    Tilleggsopplysning.KILDESKATT_PAA_PENSJON -> "kildeskattpensjonist"

                    Tilleggsopplysning.OPPHOLD_PAA_SVALBARD -> it.value

                    Tilleggsopplysning.OPPHOLD_I_TILTAKSSONE -> it.value

                    else -> null
                }
            }
        if (filtered.isEmpty()) {
            return ""
        } else {
            return filtered.first()
        }
    }

    private fun formaterAntallSkattekortMedIMelding(): String = gyldigeForskuddstrekk().size.toString()

    // end-header
    private fun formaterForskuddstrekk(): String {
        val sb = StringBuilder()

        gyldigeForskuddstrekk().map { skt: Forskuddstrekk ->
            when (skt) {
                is Tabellkort -> {
                    sb.append("Trekktabell".padEnd(12))
                    sb.append(skt.trekkode.value.padEnd(55))
                    sb.append(skt.tabellNummer.padEnd(4))
                    sb.append(formaterProsentsats(skt.prosentSats).padEnd(6))
                    sb.append("".padEnd(7))
                    sb.append(formaterAntallManederTrekk(skt.antallMndForTrekk).padEnd(4))
                }

                is Prosentkort -> {
                    sb.append("Trekkprosent".padEnd(12))
                    sb.append(skt.trekkode.value.padEnd(55))
                    sb.append("".padEnd(4))
                    sb.append(formaterProsentsats(skt.prosentSats).padEnd(6))
                    sb.append("".padEnd(7))
                    sb.append(formaterAntallManederTrekk(skt.antallMndForTrekk).padEnd(4))
                }

                is Frikort -> {
                    sb.append("Frikort".padEnd(12))
                    sb.append(skt.trekkode.value.padEnd(55))
                    sb.append("".padEnd(4))
                    sb.append("".padEnd(6))
                    sb.append(formaterFrikortbeloep(skt))
                    sb.append("".padEnd(4))
                }
            }
        }
        return sb.toString()
    }

    private fun formaterProsentsats(prosentsats: BigDecimal?): String = dfProsentsats.format(prosentsats)

    private fun formaterAntallManederTrekk(antallManederTrekk: BigDecimal?): String = antallManederTrekk?.let { dfAntallMndTrekk.format(it) } ?: ""

    private fun formaterFrikortbeloep(frikort: Frikort): String {
        val beloep = frikort.frikortBeloep?.toString() ?: ""
        return beloep.padStart(7, if (beloep.isEmpty()) ' ' else '0')
    }

    companion object {
        private const val UTSTEDT_DATO_IKKE_SKATTEPLIKT_POSTFIX = "-01-01"
        private val symbols = DecimalFormatSymbols().apply { decimalSeparator = ',' }
        private val dfProsentsats: DecimalFormat = DecimalFormat("000.00", symbols)
        private val dfAntallMndTrekk: DecimalFormat = DecimalFormat("00.0", symbols)
    }
}
