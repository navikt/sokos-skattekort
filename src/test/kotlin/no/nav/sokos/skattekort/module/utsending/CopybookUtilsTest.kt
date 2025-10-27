package no.nav.sokos.skattekort.module.utsending

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

import no.nav.sokos.skattekort.TestData

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
            val result = CopybookUtils.skattekortToArenaCopybookFormat(skattekort)
            result.substring(2, 6) shouldBe "5444"
        }

        test("skattekortToArenaCopybookFormat med opphold svalbard") {
            val result = CopybookUtils.skattekortToArenaCopybookFormat(skattekort)
            result.substring(2, 6) shouldBe "2100"
        }
    })
