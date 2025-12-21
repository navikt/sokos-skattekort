package no.nav.sokos.skattekort.module.skattekort

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotliquery.TransactionalSession

import no.nav.sokos.skattekort.infrastructure.DbListener
import no.nav.sokos.skattekort.module.person.PersonId
import no.nav.sokos.skattekort.util.SQLUtils.transaction

class SkattekortRepositoryTest :
    FunSpec({
        extensions(DbListener)

        fun <T> tx(block: (TransactionalSession) -> T): T = DbListener.dataSource.transaction { tx -> block(tx) }

        test("Hent riktig skattekort når det finnes mange") {
            databaseHas(
                aPerson(1L, "12345678901"),
                aDbSkattekort(
                    id = 6986540,
                    personId = 1,
                    utstedtDato = "2024-12-05",
                    identifikator = "1058529683",
                    inntektsaar = 2025,
                    opprettet = "2025-12-14 17:16:10.276264",
                ),
                aDbSkattekort(
                    id = 8732414,
                    personId = 1,
                    utstedtDato = "2025-12-05",
                    identifikator = "567677175",
                    inntektsaar = 2026,
                    opprettet = "2025-12-16 19:25:47.520911",
                ),
                aDbSkattekort(
                    id = 10013248,
                    personId = 1,
                    utstedtDato = "2024-12-05",
                    identifikator = "1085419887",
                    inntektsaar = 2025,
                    opprettet = "2025-12-19 15:43:47.381757",
                ),
                aDbSkattekort(
                    id = 10014205,
                    personId = 1,
                    utstedtDato = "2024-12-05",
                    identifikator = "1085419887",
                    inntektsaar = 2025,
                    opprettet = "2025-12-19 15:46:47.541476",
                ),
                aDbSkattekort(
                    id = 10014992,
                    personId = 1,
                    utstedtDato = "2024-12-05",
                    identifikator = "1085419887",
                    inntektsaar = 2025,
                    opprettet = "2025-12-19 15:49:47.685548",
                ),
                aDbSkattekort(
                    id = 10015752,
                    personId = 1,
                    utstedtDato = "2024-12-05",
                    identifikator = "1085419887",
                    inntektsaar = 2025,
                    opprettet = "2025-12-19 15:52:47.833756",
                ),
                aDbSkattekort(
                    id = 10016224,
                    personId = 1,
                    utstedtDato = "2024-12-05",
                    identifikator = "1088125212",
                    inntektsaar = 2025,
                    opprettet = "2025-12-19 16:37:45.751951",
                ),
            )
            shouldThrow<NoSuchElementException> { tx { SkattekortRepository.findLatestByPersonId(it, PersonId(1), 2027, adminRole = false) } }
            tx { SkattekortRepository.findLatestByPersonId(it, PersonId(1), 2026, false) }.id!!.value shouldBe 8732414
            tx { SkattekortRepository.findLatestByPersonId(it, PersonId(1), 2025, false) }.id!!.value shouldBe 10016224
        }
        test("Hent riktig skattekort når to skattekort har samme opprettet-tidspunkt") {
            databaseHas(
                aPerson(1L, "12345678901"),
                aDbSkattekort(
                    id = 10015752,
                    personId = 1,
                    utstedtDato = "2024-12-05",
                    identifikator = "1085419887",
                    inntektsaar = 2025,
                    opprettet = "2025-12-19 15:52:47.833756",
                ),
                aDbSkattekort(
                    id = 10016224,
                    personId = 1,
                    utstedtDato = "2024-12-05",
                    identifikator = "1088125212",
                    inntektsaar = 2025,
                    opprettet = "2025-12-19 15:52:47.833756",
                ),
            )
            tx { SkattekortRepository.findLatestByPersonId(it, PersonId(1), 2025, false) }.id!!.value shouldBe 10016224
        }
    })
