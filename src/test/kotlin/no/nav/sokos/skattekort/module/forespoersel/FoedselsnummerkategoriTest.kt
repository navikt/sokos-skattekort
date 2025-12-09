package no.nav.sokos.skattekort.module.forespoersel

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

class FoedselsnummerkategoriTest :
    FunSpec({
        test("GYLDIGE.regel skal returnere true for gyldig fødselsnumre") {
            Foedselsnummerkategori.GYLDIGE.erGyldig("01010112345") shouldBe true
            Foedselsnummerkategori.GYLDIGE.erGyldig("61010112345") shouldBe true
        }
        test("GYLDIGE.regel skal returnere false for ugyldige fnr") {
            Foedselsnummerkategori.GYLDIGE.erGyldig shouldNotBeNull {
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
        test("TENOR.regel skal returnere false for ekte fødselsnumre og ugyldige datoer") {
            Foedselsnummerkategori.TENOR.erGyldig("01010112345") shouldBe false
            Foedselsnummerkategori.TENOR.erGyldig("31820112345") shouldBe false
        }
        test("TENOR.regel skal returnere true for tenorbrukere") {
            Foedselsnummerkategori.TENOR.erGyldig shouldNotBeNull {
                this("01810112345") shouldBe true
                this("31920112345") shouldBe true
            }
        }
        test("ALLE.regel skal returnere true så lenge det er 11-sifre") {
            Foedselsnummerkategori.ALLE.erGyldig shouldNotBeNull {
                this("01010112345") shouldBe true
                this("61010112345") shouldBe true
                this("01810112345") shouldBe true
                this("99999999999") shouldBe true
            }
        }
        test("ALLE.regel skal returnere false for feil lengde eller bokstaver") {
            Foedselsnummerkategori.ALLE.erGyldig shouldNotBeNull {
                assertSoftly {
                    withClue("Skal bli false pga feil lengde") { this("010101") shouldBe false }
                    withClue("Skal bli false pga bokstaver") { this("abcdefghijk") shouldBe false }
                }
            }
        }
    })
