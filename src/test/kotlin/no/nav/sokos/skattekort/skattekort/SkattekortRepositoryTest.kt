package no.nav.sokos.skattekort.skattekort

import java.time.LocalDate
import java.time.LocalDateTime

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import kotliquery.TransactionalSession

import no.nav.sokos.skattekort.listener.DbListener
import no.nav.sokos.skattekort.person.PersonId
import no.nav.sokos.skattekort.util.SQLUtils.transaction

class SkattekortRepositoryTest :
    BehaviorSpec({
        extensions(DbListener)

        fun <T> tx(block: (TransactionalSession) -> T): T = DbListener.dataSource.transaction { tx -> block(tx) }

        Given("flere skattekort med ulike opprettelsestidspunkt") {
            fun seedData() {
                databaseHas(
                    aPerson(1L),
                    afoedselsnummer(1L, "01010112345"),
                    aSkattekort(
                        id = 6986540,
                        personId = 1,
                        utstedtDato = LocalDate.parse("2024-12-05"),
                        identifikator = "1058529683",
                        inntektsaar = 2025,
                        opprettet = LocalDateTime.parse("2025-12-14T17:16:10.276264"),
                    ),
                    aSkattekort(
                        id = 8732414,
                        personId = 1,
                        utstedtDato = LocalDate.parse("2024-12-05"),
                        identifikator = "567677175",
                        inntektsaar = 2026,
                        opprettet = LocalDateTime.parse("2025-12-16T19:25:47.520911"),
                    ),
                    aSkattekort(
                        id = 10013248,
                        personId = 1,
                        utstedtDato = LocalDate.parse("2024-12-05"),
                        identifikator = "1085419887",
                        inntektsaar = 2025,
                        opprettet = LocalDateTime.parse("2025-12-19T15:43:47.381757"),
                    ),
                    aSkattekort(
                        id = 10014205,
                        personId = 1,
                        utstedtDato = LocalDate.parse("2024-12-05"),
                        identifikator = "1085419887",
                        inntektsaar = 2025,
                        opprettet = LocalDateTime.parse("2025-12-19T15:46:47.541476"),
                    ),
                    aSkattekort(
                        id = 10014992,
                        personId = 1,
                        utstedtDato = LocalDate.parse("2024-12-05"),
                        identifikator = "1085419887",
                        inntektsaar = 2025,
                        opprettet = LocalDateTime.parse("2025-12-19T15:49:47.685548"),
                    ),
                    aSkattekort(
                        id = 10015752,
                        personId = 1,
                        utstedtDato = LocalDate.parse("2024-12-05"),
                        identifikator = "1085419887",
                        inntektsaar = 2025,
                        opprettet = LocalDateTime.parse("2025-12-19T15:52:47.833756"),
                    ),
                    aSkattekort(
                        id = 10016224,
                        personId = 1,
                        utstedtDato = LocalDate.parse("2024-12-05"),
                        identifikator = "1088125212",
                        inntektsaar = 2025,
                        opprettet = LocalDateTime.parse("2025-12-19T16:37:45.751951"),
                    ),
                )
            }

            When("siste skattekort hentes for et gitt inntektsår") {
                Then("returneres riktig skattekort eller feil når det ikke finnes") {
                    seedData()

                    shouldThrow<NoSuchElementException> { tx { SkattekortRepository.findLatestByPersonId(it, PersonId(1), 2027, adminRole = false) } }
                    tx { SkattekortRepository.findLatestByPersonId(it, PersonId(1), 2026, false) }.id!!.value shouldBe 8732414
                    tx { SkattekortRepository.findLatestByPersonId(it, PersonId(1), 2025, false) }.id!!.value shouldBe 10016224
                }
            }
        }

        Given("to skattekort med samme opprettet-tidspunkt") {
            fun seedData() {
                databaseHas(
                    aPerson(1L),
                    afoedselsnummer(1L, "01010112345"),
                    aSkattekort(
                        id = 10015752,
                        personId = 1,
                        utstedtDato = LocalDate.parse("2024-12-05"),
                        identifikator = "1085419887",
                        inntektsaar = 2025,
                        opprettet = LocalDateTime.parse("2025-12-19T15:52:47.833756"),
                    ),
                    aSkattekort(
                        id = 10016224,
                        personId = 1,
                        utstedtDato = LocalDate.parse("2024-12-05"),
                        identifikator = "1088125212",
                        inntektsaar = 2025,
                        opprettet = LocalDateTime.parse("2025-12-19T15:52:47.833756"),
                    ),
                )
            }

            When("siste skattekort hentes") {
                Then("velges riktig skattekort selv om opprettet-tidspunktet er likt") {
                    seedData()

                    tx { SkattekortRepository.findLatestByPersonId(it, PersonId(1), 2025, false) }.id!!.value shouldBe 10016224
                }
            }
        }
    })
