package no.nav.sokos.skattekort.module.utsending.arena

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

import no.nav.sokos.skattekort.TestData

class CopybookUtilsTest :
    FunSpec({
        val skattekort = TestData.getSkattekortTestData()
        val skattekortDelList = TestData.getSkatteDelListTestData()
        val skattekortTileggsopplysningList = TestData.getSkattekortTileggsopplysningListTestData()

        test("SkattekortToCopybookFormat with tabell") {
            val result =
                CopybookUtils.skattekortToArenaCopybookFormat(
                    skattekort,
                    skattekortDelList,
                    emptyList(),
                )
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
    })
