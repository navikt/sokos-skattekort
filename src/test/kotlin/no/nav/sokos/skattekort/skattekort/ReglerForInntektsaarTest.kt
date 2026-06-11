package no.nav.sokos.skattekort.skattekort

import java.time.LocalDateTime
import java.time.LocalDateTime.now

import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.time.withConstantNow
import io.kotest.matchers.shouldBe

class ReglerForInntektsaarTest :
    FunSpec({
        test("skalBestilleForNesteAarOgsaa if current year after 15.12.") {
            withConstantNow(LocalDateTime.of(now().year, 12, 15, 12, 0)) {
                ReglerForInntektsaar.skalBestilleForNesteAarOgsaa(now().year) shouldBe true
            }
        }
        test("Not skalBestilleForNesteAarOgsaa if current year before 15.12.") {
            withConstantNow(LocalDateTime.of(now().year, 12, 14, 12, 0)) {
                ReglerForInntektsaar.skalBestilleForNesteAarOgsaa(now().year) shouldBe false
            }
        }
        test("Not skalBestilleForNesteAarOgsaa if last year after 15.12.") {
            withConstantNow(LocalDateTime.of(now().year, 12, 15, 0, 0)) {
                ReglerForInntektsaar.skalBestilleForNesteAarOgsaa(now().year - 1) shouldBe false
            }
        }
        test("Not skalBestilleForNesteAarOgsaa if last year before 15.12.") {
            withConstantNow(LocalDateTime.of(now().year, 1, 1, 12, 0)) {
                ReglerForInntektsaar.skalBestilleForNesteAarOgsaa(now().year - 1) shouldBe false
            }
        }

        test("maxInntektsaar returns current year before Dec 15") {
            withConstantNow(LocalDateTime.of(2026, 12, 14, 12, 0)) {
                ReglerForInntektsaar.maxInntektsaar() shouldBe 2026
            }
        }

        test("maxInntektsaar returns next year from Dec 15") {
            withConstantNow(LocalDateTime.of(2026, 12, 15, 0, 0)) {
                ReglerForInntektsaar.maxInntektsaar() shouldBe 2027
            }
        }

        test("lovligeInntektsAarAaBestilleFraSkatteetaten uses previous year up to and including June") {
            withConstantNow(LocalDateTime.of(2026, 6, 1, 12, 0)) {
                ReglerForInntektsaar.lovligeInntektsAarAaBestilleFraSkatteetaten() shouldBe listOf(2025, 2026)
            }
        }

        test("lovligeInntektsAarAaBestilleFraSkatteetaten uses current year from July") {
            withConstantNow(LocalDateTime.of(2026, 7, 1, 12, 0)) {
                ReglerForInntektsaar.lovligeInntektsAarAaBestilleFraSkatteetaten() shouldBe listOf(2026)
            }
        }

        test("lovligeInntektsAarAaBestilleFraSkatteetaten uses current year after 15 December") {
            withConstantNow(LocalDateTime.of(2026, 12, 15, 12, 0)) {
                ReglerForInntektsaar.lovligeInntektsAarAaBestilleFraSkatteetaten() shouldBe listOf(2026, 2027)
            }
        }

        test("inntektsaarAaBestille filters to current year and later") {
            withConstantNow(LocalDateTime.of(2026, 6, 1, 12, 0)) {
                ReglerForInntektsaar.inntektsaarAaBestille() shouldBe listOf(2026)
            }
        }

        test("inntektsaarAaBestille includes next year after Dec 15") {
            withConstantNow(LocalDateTime.of(2026, 12, 15, 12, 0)) {
                ReglerForInntektsaar.inntektsaarAaBestille() shouldBe listOf(2026, 2027)
            }
        }

        test("alleLovligeInntektsaarAaHenteSkattekortFor always starts at previous year") {
            withConstantNow(LocalDateTime.of(2026, 1, 10, 12, 0)) {
                ReglerForInntektsaar.alleLovligeInntektsaarAaHenteSkattekortFor() shouldBe listOf(2025, 2026)
            }
        }

        test("alleLovligeInntektsaarAaHenteSkattekortFor includes next year after Dec 15") {
            withConstantNow(LocalDateTime.of(2026, 12, 15, 12, 0)) {
                ReglerForInntektsaar.alleLovligeInntektsaarAaHenteSkattekortFor() shouldBe listOf(2025, 2026, 2027)
            }
        }
    })
