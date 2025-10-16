package no.nav.sokos.skattekort.domain.utsending.oppdragz

import java.math.BigDecimal
import javax.xml.datatype.DatatypeFactory

import org.hamcrest.CoreMatchers
import org.hamcrest.MatcherAssert.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull

class SkattekortFixedRecordFormatterTest {
    val skattekortMedForskuddstrekk =
        Skattekortmelding(
            2017,
            "21048200130",
            Resultatstatus.SKATTEKORTOPPLYSNINGER_OK,
            Skattekort(
                2017,
                DatatypeFactory.newInstance().newXMLGregorianCalendar("2017-04-15"),
                2017005,
                listOf(
                    Trekktabell(
                        Trekkode.LOENN_FRA_NAV,
                        Tabelltype.TREKKTABELL_FOR_LOENN,
                        "7131",
                        BigDecimal("32"),
                        BigDecimal("10.5"),
                    ),
                    Trekkprosent(
                        Trekkode.LOENN_FRA_BIARBEIDSGIVER,
                        BigDecimal("32"),
                    ),
                    Trekkprosent(
                        Trekkode.LOENN_FRA_NAV,
                        BigDecimal("17.8"),
                    ),
                ),
            ),
            emptyList(),
        )
    val skattekortUtenForskuddstrekk =
        Skattekortmelding(
            2017,
            "12097100500",
            Resultatstatus.VURDER_ARBEIDSTILLATELSE,
            null,
            emptyList(),
        )
    val skattekortIkkeTrekkpliktig =
        Skattekortmelding(
            2017,
            "12097100500",
            Resultatstatus.IKKE_TREKKPLIKT,
            null,
            emptyList(),
        )

    @Test
    @Throws(Exception::class)
    fun skalReturnereSkattekortSomInneholderForskuddstrekkMedGyldigResultatstatusV1() {
        val skattekortmelding: Skattekortmelding = skattekortMedForskuddstrekk
        val formatertSkattekort = SkattekortFixedRecordFormatter(skattekortmelding, "2017")

        val result: String = formatertSkattekort.format()
        assertEquals(
            "21048200130skattekortopplysningerOK                20172017-04-152017005                                                     2Trekktabell loennFraNAV                                            7131032,00       10,5TrekkprosentloennFraNAV                                                017,80           ",
            result,
        )

        assertNotNull(result)
        assertFalse(result.isEmpty())

        val prosentsats = result.substring(197, 203)

        val expLength = 6
        assertThat(expLength, CoreMatchers.equalTo(prosentsats.length))

        val expValue = "032,00"
        assertEquals(expValue, prosentsats)

        val maxLength = 794
        assertTrue(result.length < maxLength)
    }

    @Test
    @Throws(Exception::class)
    fun skalReturnereSkattekortSomInneholderForskuddstrekkMedGyldigResultatstatusV2() {
        val skattekortmelding: Skattekortmelding = skattekortMedForskuddstrekk
        val formatertSkattekort = SkattekortFixedRecordFormatter(skattekortmelding, "2018")

        val result: String = formatertSkattekort.format()

        assertNotNull(result)
        assertFalse(result.isEmpty())
        assertEquals(
            "21048200130skattekortopplysningerOK                20182017-04-152017005                                                     2Trekktabell loennFraNAV                                            7131032,00       10,5TrekkprosentloennFraNAV                                                017,80           ",
            result,
        )

        val prosentsats = result.substring(197, 203)

        val expLength = 6
        assertThat(expLength, CoreMatchers.equalTo(prosentsats.length))

        val expValue = "032,00"
        assertEquals(expValue, prosentsats)

        val maxLength = 794
        assertTrue(result.length < maxLength)
    }

    @Test
    @Throws(Exception::class)
    fun skalReturnereEmptyForSkattekortSomIkkeInneholderForskuddstrekk() { // TODO: Er dette virkelig riktig?
        val skattekortmelding: Skattekortmelding = skattekortUtenForskuddstrekk
        val formatertSkattekort = SkattekortFixedRecordFormatter(skattekortmelding, "2017")

        val result: String = formatertSkattekort.format()
        assertNotNull(result)
        assertTrue(result.isEmpty())
    }

    @Test
    @Throws(Exception::class)
    fun skalReturnereSkattekortForIkkeTrekkpliktigSomIkkeInneholderForskuddstrekk() {
        val skattekortmelding = skattekortIkkeTrekkpliktig

        val formatertSkattekort = SkattekortFixedRecordFormatter(skattekortmelding, "2017")

        val result = formatertSkattekort.format()
        assertEquals(
            "12097100500ikkeTrekkplikt                          20172017-01-01                                                            1Frikort     loennFraNAV                                                                 ",
            result,
        )

        val maxLength = 794
        assertTrue(result.length < maxLength)
    }
}
