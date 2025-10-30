package no.nav.sokos.skattekort.module.utsending

import kotlin.time.ExperimentalTime
import kotlinx.datetime.LocalDate

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

import no.nav.sokos.skattekort.TestData
import no.nav.sokos.skattekort.TestData.getTilleggsopplysningListTestData
import no.nav.sokos.skattekort.module.skattekort.Frikort
import no.nav.sokos.skattekort.module.skattekort.Prosentkort
import no.nav.sokos.skattekort.module.skattekort.Tabellkort
import no.nav.sokos.skattekort.module.skattekort.Tilleggsopplysning
import no.nav.sokos.skattekort.module.utsending.oppdragz.Tilleggsopplysning.OPPHOLD_PAA_SVALBARD
import no.nav.sokos.skattekort.module.utsending.oppdragz.Trekkode

@OptIn(ExperimentalTime::class)
class CopybookUtilsTest :
    FunSpec({
        val skattekort = TestData.getSkattekortTestData()

        val skattekortTileggsopplysningList = TestData.getTilleggsopplysningListTestData()

        test("skattekortToArenaCopybookFormat med tabell trekk") {
            val result = CopybookUtils.skattekortToArenaCopybookFormat(skattekort)
            result shouldBe "SO0301" +
                "20860599016" +
                "1 7100 35" +
                "00" +
                "30042025" +
                "0" +
                "00" +
                "2025" +
                "      " +
                "/n"
        }

        test("skattekortToArenaCopybookFormat med opphold tiltakssone") {
            val result =
                CopybookUtils.skattekortToArenaCopybookFormat(
                    skattekort.copy(tilleggsopplysningList = getTilleggsopplysningListTestData()),
                )
            result.substring(2, 6) shouldBe "5444"
        }

        test("skattekortToArenaCopybookFormat med opphold svalbard") {
            val result =
                CopybookUtils.skattekortToArenaCopybookFormat(
                    skattekort.copy(tilleggsopplysningList = listOf(Tilleggsopplysning(opplysning = OPPHOLD_PAA_SVALBARD.value))),
                )
            result.substring(2, 6) shouldBe "2100"
        }

        test("skattekortToArenaCopybookFormat med prosentkort") {
            val prosentkort =
                Prosentkort(
                    trekkode = Trekkode.LOENN_FRA_NAV.value,
                    prosentSats = 17.0.toBigDecimal(),
                )
            val s = skattekort.copy(forskuddstrekkList = listOf(prosentkort))
            val result = CopybookUtils.skattekortToArenaCopybookFormat(s)
            // Format: SO + 4 kommune + 11 ident + '2' + (skatteklasse blank) + 4 blanks + blank + prosent (17) + ...
            result.substring(0, 2) shouldBe "SO"
            result.substring(2, 6) shouldBe "0301"
            result.substring(6, 17).trim() shouldBe s.identifikator
            result[17] shouldBe '2' // prosentkort indikator
            result.substring(18, 23).isBlank() shouldBe true // tabellnummer blank
            result.substring(24, 26).trim() shouldBe "17" // prosentSats
        }

        test("skattekortToArenaCopybookFormat med frikort med beløp") {
            val frikort =
                Frikort(
                    trekkode = Trekkode.LOENN_FRA_NAV.value,
                    frikortBeloep = 1234,
                )
            val s = skattekort.copy(forskuddstrekkList = listOf(frikort))
            val result = CopybookUtils.skattekortToArenaCopybookFormat(s)
            result[17] shouldBe '5' // frikort med beløp indikator
            result.takeLast(8).startsWith("1234") shouldBe true
        }

        test("getFrikortBeloepCopybookFormat beregner posisjon korrekt") {
            val frikort =
                Frikort(
                    trekkode = Trekkode.LOENN_FRA_NAV.value,
                    frikortBeloep = 99999,
                )
            val s = skattekort.copy(forskuddstrekkList = listOf(frikort))
            val out = CopybookUtils.getFrikortBeloepCopybookFormat(s, listOf(frikort))
            // Beløp length 5 => diff 1 => pos = 17 + 1 = 18
            out.startsWith("${s.identifikator}-18s 99999") shouldBe true
        }

        test("skattekortToArenaCopybookFormat returnerer tom streng når LOENN_FRA_NAV mangler") {
            val annenTrekk =
                Tabellkort(
                    trekkode = "ANNEN",
                    tabellNummer = "7100",
                    prosentSats = 35.0.toBigDecimal(),
                    antallMndForTrekk = 0.5.toBigDecimal(),
                )
            val s = skattekort.copy(forskuddstrekkList = listOf(annenTrekk))
            CopybookUtils.skattekortToArenaCopybookFormat(s) shouldBe ""
        }

        test("Utskriftsdato format ddMMyyyy") {
            val tabellkort =
                Tabellkort(
                    trekkode = Trekkode.LOENN_FRA_NAV.value,
                    tabellNummer = "7100",
                    prosentSats = 35.0.toBigDecimal(),
                    antallMndForTrekk = 0.5.toBigDecimal(),
                )
            val customDateSkattekort =
                skattekort.copy(
                    utstedtDato = LocalDate(2025, 1, 2),
                    forskuddstrekkList = listOf(tabellkort),
                )
            val result = CopybookUtils.skattekortToArenaCopybookFormat(customDateSkattekort)
            result.contains("02012025") shouldBe true
        }
    })
