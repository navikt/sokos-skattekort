package no.nav.sokos.skattekort.module.utsending

import kotlinx.datetime.LocalDate
import kotlinx.datetime.format
import kotlinx.datetime.format.FormatStringsInDatetimeFormats
import kotlinx.datetime.format.byUnicodePattern

import no.nav.sokos.skattekort.module.skattekort.Skattekort
import no.nav.sokos.skattekort.module.skattekort.SkattekortDel
import no.nav.sokos.skattekort.module.skattekort.SkattekortType
import no.nav.sokos.skattekort.module.skattekort.Tileggsopplysning
import no.nav.sokos.skattekort.module.utsending.oppdragz.Tilleggsopplysning.OPPHOLD_I_TILTAKSSONE
import no.nav.sokos.skattekort.module.utsending.oppdragz.Trekkode

private const val OPPHOLD_TILTAKSSONE = "5444"
private const val OPPHOLD_PA_SVALBARD = "2100"
private const val DEFAULT_OPPHOLD_OPPLYSNING = "0301"

private const val TREKKTABELL = "1"
private const val TREKKPROSENT = "2"
private const val FRIKORT_UTEN_BELOP = "4"
private const val FRIKORT_MED_BELOP = "5"

private const val RECORD_TYPE = "SO"
private const val EMPTY_SPACE = ' '

private const val MAX_LENGDE_FRIKORT_BELOP = 6
private const val MAX_LENGDE_FNR = 17

object CopybookUtils {
    fun skattekortToArenaCopybookFormat(skattekort: Skattekort): String {
        val skattekortDel = findSkattekortDel(skattekort.skattekortDelList) ?: return ""

        return StringBuilder()
            .append(formatRecordType())
            .append(formatSkattekommune(skattekort.tileggsopplysningList))
            .append(formatArbeidstakeridentifikator(skattekort))
            .append(formatForskuddsform(skattekortDel))
            .append(formatNyTrekkmnd())
            .append(formatUtskriftsdato(skattekort))
            .append(formatArbeidsgivergruppe())
            .append(formatSkattemantallsgruppe())
            .append(formatInntektsaar(skattekort))
            .append(formatFrikortbeloep(skattekortDel))
            .append("/n")
            .toString()
    }

    fun getFrikortBeloepCopybookFormat(
        skattekort: Skattekort,
        skattekortDelList: List<SkattekortDel>,
    ): String {
        val skattekortDel = findSkattekortDel(skattekortDelList)!!
        val frikortBeloepLength = skattekortDel.frikortBeloep.toString().length
        val diffLength = MAX_LENGDE_FRIKORT_BELOP - frikortBeloepLength
        val pos = MAX_LENGDE_FNR + diffLength

        return "${formatArbeidstakeridentifikator(skattekort)}-${pos}s ${formatFrikortbeloep(skattekortDel)}-12s"
    }

    private fun findSkattekortDel(skattekortDelList: List<SkattekortDel>): SkattekortDel? = skattekortDelList.find { skattekortDel -> Trekkode.LOENN_FRA_NAV.value == skattekortDel.trekkKode }

    private fun formatRecordType() = RECORD_TYPE.padEnd(2, EMPTY_SPACE)

    private fun formatSkattekommune(tileggsopplysningList: List<Tileggsopplysning>): String {
        val tilleggsopplysning =
            when {
                tileggsopplysningList.isEmpty() -> DEFAULT_OPPHOLD_OPPLYSNING
                OPPHOLD_I_TILTAKSSONE.value == tileggsopplysningList.first().opplysning -> OPPHOLD_TILTAKSSONE
                OPPHOLD_PA_SVALBARD == tileggsopplysningList.first().opplysning -> OPPHOLD_PA_SVALBARD
                else -> DEFAULT_OPPHOLD_OPPLYSNING
            }
        return tilleggsopplysning.padEnd(4, EMPTY_SPACE)
    }

    private fun formatArbeidstakeridentifikator(skattekort: Skattekort) = skattekort.identifikator.padEnd(11, EMPTY_SPACE)

    private fun formatForskuddsform(skattekortDel: SkattekortDel): String {
        val sb = StringBuilder()

        val skatteklasse = "".padEnd(1, EMPTY_SPACE)
        val trekkTabelltype = "".padEnd(1, EMPTY_SPACE)

        when (skattekortDel.skattekortType) {
            SkattekortType.TABELL -> {
                sb.append(TREKKTABELL.padEnd(1, EMPTY_SPACE))
                sb.append(skatteklasse)
                sb.append((skattekortDel.tabellNummer ?: "").padEnd(4, EMPTY_SPACE))
                sb.append(trekkTabelltype)
                sb.append((skattekortDel.prosentsats?.toInt()?.toString() ?: "").padEnd(2, EMPTY_SPACE))
            }

            SkattekortType.PROSENT -> {
                sb.append(TREKKPROSENT.padEnd(1, EMPTY_SPACE))
                sb.append(skatteklasse)
                sb.append("".padEnd(4, EMPTY_SPACE))
                sb.append(trekkTabelltype)
                sb.append((skattekortDel.prosentsats?.toInt()?.toString() ?: "").padEnd(2, EMPTY_SPACE))
            }

            SkattekortType.FRIKORT -> {
                skattekortDel.frikortBeloep.let {
                    sb.append(FRIKORT_MED_BELOP.padEnd(1, EMPTY_SPACE))
                } ?: sb.append(FRIKORT_UTEN_BELOP.padEnd(1, EMPTY_SPACE))
                sb.append(skatteklasse)
                sb.append("".padEnd(4, EMPTY_SPACE))
                sb.append(trekkTabelltype)
                sb.append("".padEnd(2, EMPTY_SPACE))
            }
        }
        return sb.toString()
    }

    private fun formatNyTrekkmnd() = "00"

    @OptIn(FormatStringsInDatetimeFormats::class)
    private fun formatUtskriftsdato(skattkort: Skattekort): String {
        val customFormat = LocalDate.Format { byUnicodePattern("ddMMyyyy") }
        return skattkort.utstedtDato.format(customFormat).padEnd(8, EMPTY_SPACE)
    }

    private fun formatArbeidsgivergruppe() = "0"

    private fun formatSkattemantallsgruppe() = "00"

    private fun formatInntektsaar(skattekort: Skattekort) = skattekort.inntektsaar.toString()

    private fun formatFrikortbeloep(skattekortDel: SkattekortDel): String {
        if (skattekortDel.skattekortType != SkattekortType.FRIKORT) {
            return "".padEnd(6, EMPTY_SPACE)
        }
        return skattekortDel.frikortBeloep.toString().padEnd(6, EMPTY_SPACE)
    }
}
