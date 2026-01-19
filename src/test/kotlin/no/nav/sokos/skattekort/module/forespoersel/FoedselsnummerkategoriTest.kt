package no.nav.sokos.skattekort.module.forespoersel

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

class FoedselsnummerkategoriTest :
    FunSpec({
        test("gyldig fødselsnumre kan bestille skattekort etter reglene for GYLDIGE") {
            Foedselsnummerkategori.GYLDIGE.kanBestilleSkattekort("01010112345") shouldBe true
            Foedselsnummerkategori.GYLDIGE.kanBestilleSkattekort("61010112345") shouldBe true
        }
        test("ugyldige fnr kan ikke bestille skattekort etter reglene for GYLDIGE") {
            Foedselsnummerkategori.GYLDIGE.kanBestilleSkattekort shouldNotBeNull {
                assertSoftly {
                    withClue("dag > 31") { this("32010112345") shouldBe false }
                    withClue("måned > 12") { this("01130112345") shouldBe false }
                    withClue("februar har ikke 30 dager") { this("30020112345") shouldBe false }
                    withClue("april har ikke 31 dager") { this("31040112345") shouldBe false }
                    withClue("feil måned") { this("31140112345") shouldBe false }
                    withClue("skal ikke godta tenor-fnr") { this("31840112345") shouldBe false }
                    withClue("Skal bli false pga feil lengde") { this("010101") shouldBe false }
                    withClue("Skal bli false pga bokstaver") { this("abcdefghijk") shouldBe false }
                    withClue("Skal bli false pga bokstaver") { this("a") shouldBe false }
                }
            }
        }
        test("KUNSTIGE_FNR.kan bestille skattekort skal returnere false for ekte fødselsnumre og ugyldige datoer") {
            Foedselsnummerkategori.KUNSTIGE_FNR.kanBestilleSkattekort("01010112345") shouldBe false
            Foedselsnummerkategori.KUNSTIGE_FNR.kanBestilleSkattekort("31820112345") shouldBe false
        }
        test("KUNSTIGE_FNR.regel skal returnere true for tenorbrukere") {
            Foedselsnummerkategori.KUNSTIGE_FNR.kanBestilleSkattekort shouldNotBeNull {
                this("01810112345") shouldBe true
                this("31920112345") shouldBe true
            }
        }
        test("KUNSTIGE_FNR skal returnere true for dollybrukere") {
            Foedselsnummerkategori.KUNSTIGE_FNR.erGyldig shouldNotBeNull {
                this("01410112345") shouldBe true
                this("31520112345") shouldBe true
            }
        }
        test("KUNSTIGE_FNR dollybrukere skal ikke kunne bestille skattekort") {
            Foedselsnummerkategori.KUNSTIGE_FNR.kanBestilleSkattekort shouldNotBeNull {
                this("01410112345") shouldBe false
                this("31520112345") shouldBe true
            }
        }
        test("ALLE.regel skal returnere true så lenge det er 11-sifre") {
            Foedselsnummerkategori.ALLE.kanBestilleSkattekort shouldNotBeNull {
                this("01010112345") shouldBe true
                this("61010112345") shouldBe true
                this("01810112345") shouldBe true
                this("99999999999") shouldBe true
            }
        }
        test("ALLE.regel skal returnere false for feil lengde eller bokstaver") {
            Foedselsnummerkategori.ALLE.kanBestilleSkattekort shouldNotBeNull {
                assertSoftly {
                    withClue("Skal bli false pga feil lengde") { this("010101") shouldBe false }
                    withClue("Skal bli false pga bokstaver") { this("abcdefghijk") shouldBe false }
                }
            }
        }
    })
