package no.nav.sokos.skattekort.skattekort

import java.time.LocalDate
import java.time.LocalDateTime

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

import no.nav.sokos.skattekort.listener.DbListener
import no.nav.sokos.skattekort.person.PersonId
import no.nav.sokos.skattekort.utils.TestUtils.tx

class SkattekortRepositoryTest :
    FunSpec({
        extensions(DbListener)

        test("Hent riktig skattekort når det finnes mange") {
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
            tx { SkattekortRepository.findAllByPersonId(it, listOf(PersonId(1)), listOf(2027), showOnlyLatest = true, adminRole = false) } shouldBe emptyList()
            tx { SkattekortRepository.findAllByPersonId(it, listOf(PersonId(1)), listOf(2026), showOnlyLatest = true, adminRole = false) }.first().id!!.value shouldBe 8732414
            tx { SkattekortRepository.findAllByPersonId(it, listOf(PersonId(1)), listOf(2025), showOnlyLatest = true, adminRole = false) }.first().id!!.value shouldBe 10016224
        }
        test("Hent riktig skattekort når to skattekort har samme opprettet-tidspunkt") {
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
            tx { SkattekortRepository.findAllByPersonId(it, listOf(PersonId(1)), listOf(2025), showOnlyLatest = true, adminRole = false) }.first().id!!.value shouldBe 10016224
        }
    })
