package no.nav.sokos.skattekort.domain.utsending.oppdragz

import org.hamcrest.CoreMatchers
import org.hamcrest.MatcherAssert.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull

import no.nav.sokos.skattekort.domain.utsending.oppdragz.TestObjects.skattekortIkkeTrekkpliktig
import no.nav.sokos.skattekort.domain.utsending.oppdragz.TestObjects.skattekortMedForskuddstrekk

class SkattekortFixedRecordFormatterTest {
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
