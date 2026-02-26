package no.nav.sokos.skattekort.forespoersel

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

class FoedselsnummerkategoriTest :
    FunSpec({
        test("Man skal kunne bestille skattekort for fnr og dnr med gyldig fødselsdato etter reglene for GYLDIGE") {
            Foedselsnummerkategori.GYLDIGE.kanBestilleSkattekort("01010112345") shouldBe true
            Foedselsnummerkategori.GYLDIGE.kanBestilleSkattekort("61010112345") shouldBe true
        }
        test("Man skal ikke kunne bestille skattekort med feil i fnr etter reglene for GYLDIGE") {
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
        test("Man skal ikke kunne bestille skattekort for tilsynelatende ekte fødselsnumre og ugyldige datoer etter regler for KUNSTIGE_FNR") {
            Foedselsnummerkategori.KUNSTIGE_FNR.kanBestilleSkattekort("01010112345") shouldBe false
            Foedselsnummerkategori.KUNSTIGE_FNR.kanBestilleSkattekort("31820112345") shouldBe false
        }
        test("Man skal kunne bestille skattekort for tenorbrukere etter reglene for KUNSTIGE_FNR") {
            Foedselsnummerkategori.KUNSTIGE_FNR.kanBestilleSkattekort shouldNotBeNull {
                this("01810112345") shouldBe true
                this("31920112345") shouldBe true
            }
        }
        test("Dollybrukere skal være gyldige etter reglene for KUNSTIGE_FNR") {
            Foedselsnummerkategori.KUNSTIGE_FNR.erGyldig shouldNotBeNull {
                this("01410112345") shouldBe true
                this("31520112345") shouldBe true
            }
        }
        test("Man skal kunne bestille skattekort for Dollybrukere etter reglene for KUNSTIGE_FNR") {
            Foedselsnummerkategori.KUNSTIGE_FNR.kanBestilleSkattekort shouldNotBeNull {
                this("01410112345") shouldBe false
                this("31520112345") shouldBe false
            }
        }
        test("Dollybrukere, Testnorge-brukere, reelle fnr og alle fnr med 11 sifre er gyldige etter reglene for ALLE") {
            Foedselsnummerkategori.ALLE.erGyldig shouldNotBeNull {
                this("01010112345") shouldBe true
                this("61010112345") shouldBe true
                this("01810112345") shouldBe true
                this("99999999999") shouldBe true
            }
        }
        test("Feil lengde eller bokstaver i fnr er ugyldig etter reglene for ALLE") {
            Foedselsnummerkategori.ALLE.erGyldig shouldNotBeNull {
                assertSoftly {
                    withClue("Skal bli false pga feil lengde") { this("010101") shouldBe false }
                    withClue("Skal bli false pga bokstaver") { this("abcdefghijk") shouldBe false }
                }
            }
        }
    })
