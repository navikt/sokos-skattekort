package no.nav.sokos.skattekort.module.utsending.oppdragz

import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols

import org.apache.commons.lang3.StringUtils.leftPad
import org.apache.commons.lang3.StringUtils.rightPad
import org.apache.commons.lang3.StringUtils.substring

import no.nav.sokos.skattekort.module.skattekort.Forskuddstrekk
import no.nav.sokos.skattekort.module.skattekort.Frikort
import no.nav.sokos.skattekort.module.skattekort.Prosentkort
import no.nav.sokos.skattekort.module.skattekort.ResultatForSkattekort
import no.nav.sokos.skattekort.module.skattekort.Tabellkort
import no.nav.sokos.skattekort.module.skattekort.Tilleggsopplysning
import no.nav.sokos.skattekort.module.skattekort.Trekkode

class SkattekortFixedRecordFormatter internal constructor(
    private val skattekortmelding: Skattekortmelding,
    private val inntektsaar: String?,
) {
    private val simulertSkattekort = Frikort(Trekkode.LOENN_FRA_NAV, null)

    private fun gyldigeForskuddstrekk(): List<Forskuddstrekk> {
        if (erIkkeTrekkPliktig() && !inneholderSkattekort()) {
            return listOf(simulertSkattekort) // TODO: Dette er vel ikke riktig for ikke trekkpliktige? De skal ha noe standardsatser, yesno?
        } else {
            return skattekortmelding.skattekort
                ?.forskuddstrekk
                ?.mapNotNull { t ->
                    when (t.trekkode()) {
                        Trekkode.LOENN_FRA_NAV -> t
                        Trekkode.PENSJON_FRA_NAV -> t
                        Trekkode.UFOERETRYGD_FRA_NAV -> t
                        else -> null
                    }
                } ?: emptyList()
        }
    }

    private fun inneholderSkattekort(): Boolean = (skattekortmelding.skattekort != null)

    private fun erIkkeTrekkPliktig(): Boolean = ResultatForSkattekort.IkkeTrekkplikt.equals(skattekortmelding.resultatPaaForespoersel)

    fun format(): String {
        val frSkattekort = StringBuilder()
        if ((inneholderSkattekort() || erIkkeTrekkPliktig()) && finnesGyldigTrekkode()) {
            frSkattekort
                .append(formaterFnr())
                .append(formaterResultatPaaForesporsel())
                .append(rightPad(inntektsaar, 4))
                .append(formaterUtstedtDato())
                .append(formaterSkattekortidentifikator())
                .append(formaterTilleggsopplysning())
                .append(formaterAntallSkattekortMedIMelding())
                .append(formaterForskuddstrekk())
        }
        return frSkattekort.toString()
    }

    private fun formaterFnr(): String = rightPad(skattekortmelding.arbeidstakeridentifikator, 11)

    private fun formaterResultatPaaForesporsel(): String {
        val resultat: String = skattekortmelding.resultatPaaForespoersel.value
        // Maks 40 posisjoner i fixedfield format til OS
        if (resultat.length > 40) {
            return substring(resultat, 0, 40)
        } else {
            return rightPad(resultat, 40)
        }
    }

    private fun formaterUtstedtDato(): String {
        if (erIkkeTrekkPliktig() && !inneholderSkattekort()) {
            val utstedtDato = inntektsaar + UTSTEDT_DATO_IKKE_SKATTEPLIKT_POSTFIX
            return rightPad(utstedtDato, 10)
        } else if (ResultatForSkattekort.IkkeSkattekort.equals(skattekortmelding.resultatPaaForespoersel)) {
            return rightPad("", 10)
        }
        return rightPad(skattekortmelding.skattekort?.utstedtDato?.toString() ?: "", 10)
    }

    private fun formaterSkattekortidentifikator(): String {
        val skattekortidentifikator: String
        if ((erIkkeTrekkPliktig() && !inneholderSkattekort()) || ResultatForSkattekort.IkkeSkattekort.equals(skattekortmelding.resultatPaaForespoersel)) {
            skattekortidentifikator = ""
        } else {
            skattekortidentifikator = skattekortmelding.skattekort?.skattekortidentifikator?.toString() ?: ""
        }
        return rightPad(skattekortidentifikator, 10)
    }

    private fun formaterTilleggsopplysning(): String {
        val tilleggopplysninger: List<Tilleggsopplysning> = skattekortmelding.tilleggsopplysning
        return rightPad(if (tilleggopplysninger.isEmpty()) "" else filterTilleggsopplysning(tilleggopplysninger), 50)
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
        return rightPad(antallSkattekort.toString(), 1)
    }

    // end-header
    private fun formaterForskuddstrekk(): String {
        val sb = StringBuilder()

        gyldigeForskuddstrekk().map { skt: Forskuddstrekk ->
            when (skt) {
                is Tabellkort -> {
                    sb.append(rightPad("Trekktabell", 12))
                    sb.append(rightPad(skt.trekkode.value, 55))
                    sb.append(rightPad(skt.tabellNummer, 4))
                    sb.append(rightPad(formaterProsentsats(skt.prosentSats), 6))
                    sb.append(rightPad("", 7))
                    sb.append(rightPad(formaterAntallManederTrekk(skt.antallMndForTrekk), 4))
                }

                is Prosentkort -> {
                    sb.append(rightPad("Trekkprosent", 12))
                    sb.append(rightPad(skt.trekkode.value, 55))
                    sb.append(rightPad("", 4))
                    sb.append(rightPad(formaterProsentsats(skt.prosentSats), 6))
                    sb.append(leftPad("", 7))
                    sb.append(rightPad(formaterAntallManederTrekk(skt.antallMndForTrekk), 4))
                }

                is Frikort -> {
                    sb.append(rightPad("Frikort", 12))
                    sb.append(rightPad(skt.trekkode.value, 55))
                    sb.append(rightPad("", 4))
                    sb.append(rightPad("", 6))
                    sb.append(finnFrikortbeloep(skt))
                    sb.append(rightPad("", 4))
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
        return leftPad(if (harIkkeFrikortBeloep) "" else frikortbeloep.toString(), 7, if (harIkkeFrikortBeloep) " " else "0")
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
