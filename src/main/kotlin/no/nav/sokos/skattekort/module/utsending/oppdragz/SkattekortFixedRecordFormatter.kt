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
    private val simulertSkattekort = Frikort(Trekkode.LOENN_FRA_NAV, null)

    private fun gyldigeForskuddstrekk(): List<Forskuddstrekk> =
        if (erIkkeTrekkPliktig()) {
            listOf(simulertSkattekort) // TODO: Dette er vel ikke riktig for ikke trekkpliktige? De skal ha noe standardsatser, yesno?
        } else {
            skattekort
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

    private fun inneholderSkattekort(): Boolean = true

    private fun erIkkeTrekkPliktig(): Boolean = ResultatForSkattekort.IkkeTrekkplikt == skattekort.resultatForSkattekort

    fun format(): String {
        val frSkattekort = StringBuilder()
        if ((inneholderSkattekort() || erIkkeTrekkPliktig()) && finnesGyldigTrekkode()) {
            frSkattekort
                .append(formaterFnr())
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

    private fun formaterFnr(): String = fnr.padEnd(11)

    private fun formaterResultatPaaForesporsel(): String {
        val resultat: String = skattekort.resultatForSkattekort.value
        // Maks 40 posisjoner i fixedfield format til OS
        if (resultat.length > 40) {
            return resultat.substring(0, 40)
        } else {
            return resultat.padEnd(40)
        }
    }

    private fun formaterUtstedtDato(): String {
        if (erIkkeTrekkPliktig()) {
            val utstedtDato = skattekort.inntektsaar.toString() + UTSTEDT_DATO_IKKE_SKATTEPLIKT_POSTFIX
            return utstedtDato.padEnd(10)
        } else if (ResultatForSkattekort.IkkeSkattekort.equals(skattekort.resultatForSkattekort)) {
            return "".padEnd(10)
        }
        return (skattekort.utstedtDato?.toString() ?: "").padEnd(10)
    }

    private fun formaterSkattekortidentifikator(): String {
        val skattekortidentifikator: String
        if ((erIkkeTrekkPliktig() && !inneholderSkattekort()) || ResultatForSkattekort.IkkeSkattekort.equals(skattekort.resultatForSkattekort)) {
            skattekortidentifikator = ""
        } else {
            skattekortidentifikator = skattekort.identifikator ?: ""
        }
        return skattekortidentifikator.padEnd(10)
    }

    private fun formaterTilleggsopplysning(): String {
        val tilleggopplysninger: List<Tilleggsopplysning> = skattekort.tilleggsopplysningList
        return (if (tilleggopplysninger.isEmpty()) "" else filterTilleggsopplysning(tilleggopplysninger)).padEnd(50)
    }

    private fun filterTilleggsopplysning(tilleggsopplysninger: List<Tilleggsopplysning>): String {
        val filtered =
            tilleggsopplysninger.mapNotNull {
                when (it) {
                    // These are mutually exclusive
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

    private fun formaterAntallSkattekortMedIMelding(): String {
        val antallSkattekort = gyldigeForskuddstrekk().size
        return antallSkattekort.toString().padEnd(1)
    }

    // end-header
    private fun formaterForskuddstrekk(): String {
        val sb = StringBuilder()

        gyldigeForskuddstrekk().map { skt: Forskuddstrekk ->
            when (skt) {
                is Tabellkort -> {
                    sb
                        .append("Trekktabell".padEnd(12))
                        .append(skt.trekkode.value.padEnd(55))
                        .append(skt.tabellNummer.padEnd(4))
                        .append(formaterProsentsats(skt.prosentSats).padEnd(6))
                        .append("".padEnd(7))
                        .append(formaterAntallManederTrekk(skt.antallMndForTrekk).padEnd(4))
                }

                is Prosentkort -> {
                    sb
                        .append("Trekkprosent".padEnd(12))
                        .append(skt.trekkode.value.padEnd(55))
                        .append("".padEnd(4))
                        .append(formaterProsentsats(skt.prosentSats).padEnd(6))
                        .append("".padStart(7))
                        .append(formaterAntallManederTrekk(skt.antallMndForTrekk).padEnd(4))
                }

                is Frikort -> {
                    sb
                        .append("Frikort".padEnd(12))
                        .append(skt.trekkode.value.padEnd(55))
                        .append("".padEnd(4))
                        .append("".padEnd(6))
                        .append(finnFrikortbeloep(skt))
                        .append("".padEnd(4))
                }
            }
        }
        return sb.toString()
    }

    private fun formaterProsentsats(prosentsats: BigDecimal?): String = dfProsentsats.format(prosentsats)

    private fun formaterAntallManederTrekk(antallManederTrekk: BigDecimal?): String {
        if (antallManederTrekk == null) {
            return ""
        }
        return dfAntallMndTrekk.format(antallManederTrekk)
    }

    private fun finnesGyldigTrekkode(): Boolean = gyldigeForskuddstrekk().isNotEmpty()

    private fun finnFrikortbeloep(frikort: Frikort): String {
        val frikortbeloep: Int? = frikort.frikortBeloep
        val harIkkeFrikortBeloep = frikortbeloep == null
        return (if (harIkkeFrikortBeloep) "" else frikortbeloep.toString()).padStart(7, if (harIkkeFrikortBeloep) ' ' else '0')
    }

    companion object {
        private const val UTSTEDT_DATO_IKKE_SKATTEPLIKT_POSTFIX = "-01-01"
        private val dfProsentsats: DecimalFormat
        private val dfAntallMndTrekk: DecimalFormat

        init {
            val symbols = DecimalFormatSymbols()
            symbols.setDecimalSeparator(',')
            dfProsentsats = DecimalFormat("000.00", symbols)
            dfAntallMndTrekk = DecimalFormat("00.0", symbols)
        }
    }
}
