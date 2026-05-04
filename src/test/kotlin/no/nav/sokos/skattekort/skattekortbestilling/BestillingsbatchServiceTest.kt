package no.nav.sokos.skattekort.skattekortbestilling

import java.time.LocalDateTime

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.extensions.time.withConstantNow
import io.kotest.inspectors.forAll
import io.kotest.matchers.collections.shouldContainAllIgnoringFields
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.mockk.coEvery
import io.mockk.mockk

import no.nav.sokos.skattekort.infrastructure.UnleashIntegration
import no.nav.sokos.skattekort.infrastructure.skatteetaten.SkatteetatenClient
import no.nav.sokos.skattekort.infrastructure.skatteetaten.SkatteetatenClientTestUtils.okBestillSkattekortResponse
import no.nav.sokos.skattekort.listener.DbListener
import no.nav.sokos.skattekort.person.PersonId
import no.nav.sokos.skattekort.person.Personidentifikator
import no.nav.sokos.skattekort.skattekort.aBatch
import no.nav.sokos.skattekort.skattekort.aBestilling
import no.nav.sokos.skattekort.skattekort.aBestillingsbatch
import no.nav.sokos.skattekort.skattekort.aBestillingsbatchWithJson
import no.nav.sokos.skattekort.skattekort.aPerson
import no.nav.sokos.skattekort.skattekort.afoedselsnummer
import no.nav.sokos.skattekort.skattekort.databaseHas
import no.nav.sokos.skattekort.skattekortbestilling.BestillingsbatchStatus.FEILET
import no.nav.sokos.skattekort.skattekortbestilling.BestillingsbatchStatus.FERDIG
import no.nav.sokos.skattekort.skattekortbestilling.BestillingsbatchStatus.NY
import no.nav.sokos.skattekort.skattekortbestilling.BestillingsbatchStatus.RETRY
import no.nav.sokos.skattekort.skattekortbestilling.BestillingsbatchType.BESTILLING
import no.nav.sokos.skattekort.skattekortbestilling.BestillingsbatchType.OPPDATERING
import no.nav.sokos.skattekort.skattekorthenting.Bestilling
import no.nav.sokos.skattekort.utils.DBTestUtils
import no.nav.sokos.skattekort.utils.TestUtils.tx

class BestillingsbatchServiceTest :
    FunSpec({
        extensions(DbListener)

        val skatteetatenClient = mockk<SkatteetatenClient>()

        val bestillingsbatchService: BestillingsbatchService by lazy {
            BestillingsbatchService(
                dataSource = DbListener.dataSource,
                skatteetatenClient = skatteetatenClient,
                featureToggles = UnleashIntegration(),
            )
        }

        test("bestillSkattekort - Hvis det er bestillinger for neste år, ikke plukk opp før 15.12.") {
            databaseHas(
                aPerson(1L),
                afoedselsnummer(personId = 1L, fnr = "01010100001"),
                aPerson(2L),
                afoedselsnummer(personId = 2L, fnr = "02020200002"),
                aPerson(3L),
                afoedselsnummer(personId = 3L, fnr = "03030300003"),
                aBestilling(1L, "01010100001", 2025, null),
                aBestilling(2L, "02020200002", 2026, null),
                aBestilling(3L, "03030300003", 2026, null),
            )

            coEvery { skatteetatenClient.bestillSkattekort(any()) } returns
                okBestillSkattekortResponse("ref1") andThen
                okBestillSkattekortResponse("ref2")

            withConstantNow(LocalDateTime.parse("2025-12-14T00:00:00")) {
                // Kaller to ganger for å sjekke at den ikke plukker opp 2026 på andre kall
                bestillingsbatchService.bestillSkattekort()
                bestillingsbatchService.bestillSkattekort()

                val bestillings: List<Bestilling> = tx(DBTestUtils::getAllBestilling)
                val batches: List<Bestillingsbatch> = tx(DBTestUtils::getAllBestillingsbatch)

                batches.size shouldBe 1
                batches.shouldContainAllIgnoringFields(
                    listOf(aBatch(id = 1L, status = NY, type = BESTILLING, bestillingsreferanse = "ref1")),
                    Bestillingsbatch::oppdatert,
                    Bestillingsbatch::opprettet,
                    Bestillingsbatch::dataSendt,
                )
                batches.first().dataSendt shouldNotBeNull {
                    this shouldContain "01010100001"
                    this shouldNotContain "02020200002"
                    this shouldNotContain "03030300003"
                }

                bestillings.size shouldBe 3
                bestillings.shouldContainAllIgnoringFields(
                    listOf(
                        Bestilling(
                            personId = PersonId(1L),
                            fnr = Personidentifikator("01010100001"),
                            inntektsaar = 2025,
                            bestillingsbatchId = BestillingsbatchId(1L),
                        ),
                        Bestilling(
                            personId = PersonId(2L),
                            fnr = Personidentifikator("02020200002"),
                            inntektsaar = 2026,
                            bestillingsbatchId = null,
                        ),
                        Bestilling(
                            personId = PersonId(3L),
                            fnr = Personidentifikator("03030300003"),
                            inntektsaar = 2026,
                            bestillingsbatchId = null,
                        ),
                    ),
                    Bestilling::oppdatert,
                    Bestilling::id,
                )
            }

            withConstantNow(LocalDateTime.parse("2025-12-15T00:00:00")) {
                bestillingsbatchService.bestillSkattekort()

                val bestillings: List<Bestilling> = tx(DBTestUtils::getAllBestilling)
                val batches: List<Bestillingsbatch> = tx(DBTestUtils::getAllBestillingsbatch)

                batches.size shouldBe 2
                batches.shouldContainAllIgnoringFields(
                    listOf(
                        aBatch(id = 1L, status = NY, type = BESTILLING, bestillingsreferanse = "ref1"),
                        aBatch(id = 2L, status = NY, type = BESTILLING, bestillingsreferanse = "ref2"),
                    ),
                    Bestillingsbatch::oppdatert,
                    Bestillingsbatch::opprettet,
                    Bestillingsbatch::id,
                    Bestillingsbatch::dataSendt,
                )
                batches.first { it.id?.id == 1L }.dataSendt shouldNotBeNull {
                    this shouldContain "01010100001"
                    this shouldNotContain "02020200002"
                    this shouldNotContain "03030300003"
                }
                batches.first { it.id?.id == 2L }.dataSendt shouldNotBeNull {
                    this shouldNotContain "01010100001"
                    this shouldContain "02020200002"
                    this shouldContain "03030300003"
                }

                bestillings.size shouldBe 3
                bestillings.shouldContainAllIgnoringFields(
                    listOf(
                        Bestilling(
                            personId = PersonId(1L),
                            fnr = Personidentifikator("01010100001"),
                            inntektsaar = 2025,
                            bestillingsbatchId = BestillingsbatchId(1L),
                        ),
                        Bestilling(
                            personId = PersonId(2L),
                            fnr = Personidentifikator("02020200002"),
                            inntektsaar = 2026,
                            bestillingsbatchId = BestillingsbatchId(2L),
                        ),
                        Bestilling(
                            personId = PersonId(3L),
                            fnr = Personidentifikator("03030300003"),
                            inntektsaar = 2026,
                            bestillingsbatchId = BestillingsbatchId(2L),
                        ),
                    ),
                    Bestilling::oppdatert,
                    Bestilling::id,
                )
            }
        }

        test("bestillOppdaterteSkattekort - Når vi gjør et kall med tom database i midten av året skal det opprettes én batch") {
            withConstantNow(LocalDateTime.parse("2025-04-12T00:00:00")) {
                coEvery { skatteetatenClient.bestillSkattekort(any()) } returns okBestillSkattekortResponse("some-bestillings-ref")
                databaseHas(
                    aPerson(1L),
                    afoedselsnummer(1L, "01010100001"),
                    aPerson(2L),
                    afoedselsnummer(2L, "02020200002"),
                    aPerson(3L),
                    afoedselsnummer(3L, "03030300003"),
                )

                bestillingsbatchService.bestillOppdaterteSkattekort()

                val batches: List<Bestillingsbatch> = tx(DBTestUtils::getAllBestillingsbatch)

                batches.shouldBeFunctionallyEquivalentTo(
                    listOf(
                        aBatch(id = 1L, bestillingsreferanse = "some-bestillings-ref", status = NY, type = OPPDATERING),
                    ),
                )
            }
        }

        test("bestillOppdaterteSkattekort - Når vi gjør et kall med tom database i slutten av desember skal det opprettes to batcher") {
            withConstantNow(LocalDateTime.parse("2025-12-20T00:00:00")) {
                coEvery { skatteetatenClient.bestillSkattekort(any()) } returnsMany
                    listOf(
                        okBestillSkattekortResponse("some-bestillings-ref1"),
                        okBestillSkattekortResponse("some-bestillings-ref2"),
                    )
                databaseHas(
                    aPerson(1L),
                    afoedselsnummer(1L, "01010100001"),
                    aPerson(2L),
                    afoedselsnummer(2L, "02020200002"),
                    aPerson(3L),
                    afoedselsnummer(3L, "03030300003"),
                )

                bestillingsbatchService.bestillOppdaterteSkattekort()

                val batches: List<Bestillingsbatch> = tx(DBTestUtils::getAllBestillingsbatch)

                assertSoftly {
                    batches shouldNotBeNull {
                        size shouldBe 2
                        first() shouldNotBeNull {
                            status shouldBe NY

                            type shouldBe OPPDATERING
                            bestillingsreferanse shouldBe "some-bestillings-ref1"
                        }
                        elementAt(1) shouldNotBeNull {
                            status shouldBe NY
                            type shouldBe OPPDATERING
                            bestillingsreferanse shouldBe "some-bestillings-ref2"
                        }
                    }
                }
            }
        }
        test("getIncompleteBestillingsbatchesWithoutJson - returnerer kun ikke-FERDIG og utelater JSON-feltene") {
            databaseHas(
                aBestillingsbatchWithJson(
                    id = 1L,
                    ref = "BR1337",
                    status = NY,
                    dataSendt = """{"sendt":"hei"}""",
                    dataMottatt = null,
                ),
                aBestillingsbatchWithJson(
                    id = 2L,
                    ref = "BR13373",
                    status = RETRY,
                    dataSendt = """{"sendt":"x"}""",
                    dataMottatt = null,
                ),
                aBestillingsbatchWithJson(
                    id = 3L,
                    ref = "BR313373",
                    status = FEILET,
                    dataSendt = """{"sendt":"y"}""",
                    dataMottatt = """{"mottatt":"her har det visst blitt litt surr"}""",
                ),
                aBestillingsbatchWithJson(
                    id = 4L,
                    ref = "BR666",
                    status = FERDIG,
                    dataSendt = """{"sendt":"z"}""",
                    dataMottatt = """{"mottatt":"ok"}""",
                ),
            )

            val dtos = bestillingsbatchService.getIncompleteBestillingsbatchesWithoutJson()

            assertSoftly(dtos) {
                withClue("Skal kun inneholde batcher som ikke er FERDIG") {
                    map { it.id } shouldContainExactlyInAnyOrder listOf(1L, 2L, 3L)
                    forAll { it.status shouldNotBe FERDIG }
                }
                withClue("dataSendt og dataMottatt skal være null i denne 'lette' responsen") {
                    forAll {
                        it.dataSendt.shouldBeNull()
                        it.dataMottatt.shouldBeNull()
                    }
                }
            }
        }
        test("rerun - setter FEILET-batch til RETRY og returnerer 1") {
            databaseHas(
                aBestillingsbatch(id = 1L, ref = "feilet", status = FEILET),
            )

            val updated = bestillingsbatchService.rerun(1L)

            val batch = tx { BestillingsbatchRepository.findById(it, 1L) }

            assertSoftly {
                updated shouldBe 1
                batch.shouldNotBeNull()
                batch.status shouldBe RETRY
            }
        }

        test("rerun - kaster IllegalArgumentException når batch ikke finnes") {
            // tom database

            val ex =
                shouldThrow<IllegalArgumentException> {
                    bestillingsbatchService.rerun(99999L)
                }
            ex.message shouldBe "Kunne ikke finne bestillingsbatch med id 99999 for rerun"
        }

        test("getAllBestillings - returnerer alle bestillinger uavhengig av batch-tilknytning") {
            databaseHas(
                aPerson(1L),
                afoedselsnummer(1L, "01010100001"),
                aPerson(2L),
                afoedselsnummer(2L, "02020200002"),
                aPerson(3L),
                afoedselsnummer(3L, "03030300003"),
                aBestillingsbatch(id = 10L, ref = "ref-a", status = NY),
                aBestilling(personId = 1L, fnr = "01010100001", inntektsaar = 2025, batchId = 10L),
                aBestilling(personId = 2L, fnr = "02020200002", inntektsaar = 2026, batchId = 10L),
                aBestilling(personId = 3L, fnr = "03030300003", inntektsaar = 2026, batchId = null),
            )

            val bestillinger = bestillingsbatchService.getAllBestillings()

            bestillinger.shouldContainAllIgnoringFields(
                listOf(
                    Bestilling(
                        personId = PersonId(1L),
                        fnr = Personidentifikator("01010100001"),
                        inntektsaar = 2025,
                        bestillingsbatchId = BestillingsbatchId(10L),
                    ),
                    Bestilling(
                        personId = PersonId(2L),
                        fnr = Personidentifikator("02020200002"),
                        inntektsaar = 2026,
                        bestillingsbatchId = BestillingsbatchId(10L),
                    ),
                    Bestilling(
                        personId = PersonId(3L),
                        fnr = Personidentifikator("03030300003"),
                        inntektsaar = 2026,
                        bestillingsbatchId = null,
                    ),
                ),
                Bestilling::id,
                Bestilling::oppdatert,
            )
        }

        context("rerun feiler for alle ikke-FEILET statuser") {
            withData(NY, FERDIG, RETRY) { status ->
                val id = status.ordinal.toLong()
                databaseHas(aBestillingsbatch(id = id, ref = "x", status = status))
                shouldThrow<IllegalArgumentException> { bestillingsbatchService.rerun(id) }
                tx { BestillingsbatchRepository.findById(it, id)!!.status } shouldBe status
            }
        }

        test("BestillingsbatchRepository.rerun - oppdaterer 0 rader når batch har annen status enn FEILET") {
            databaseHas(aBestillingsbatch(id = 101L, ref = "ny", status = NY))

            val affected = tx { BestillingsbatchRepository.rerun(it, 101L) }

            affected shouldBe 0
            tx { BestillingsbatchRepository.findById(it, 101L)!!.status } shouldBe NY
        }

        test("BestillingsbatchRepository.getFirstNotFerdigBestillingsbatch - returnerer første batch med status NY eller RETRY") {
            databaseHas(
                aBestillingsbatch(id = 1L, ref = "ref1", status = FERDIG),
                aBestillingsbatch(id = 2L, ref = "ref2", status = NY),
                aBestillingsbatch(id = 3L, ref = "ref3", status = RETRY),
            )

            val result = tx { BestillingsbatchRepository.getFirstNotFerdigBestillingsbatch(it) }

            result.shouldNotBeNull()
            result.id shouldBe BestillingsbatchId(2L)
            result.bestillingsreferanse shouldBe "ref2"
            result.status shouldBe NY
        }

        test("BestillingsbatchRepository.getFirstNotFerdigBestillingsbatch - returnerer null når ingen batcher har status NY eller RETRY") {
            databaseHas(
                aBestillingsbatch(id = 1L, ref = "ref1", status = FERDIG),
                aBestillingsbatch(id = 2L, ref = "ref2", status = FEILET),
            )

            val result = tx { BestillingsbatchRepository.getFirstNotFerdigBestillingsbatch(it) }

            result.shouldBeNull()
        }
    })

fun List<Bestillingsbatch>.shouldBeFunctionallyEquivalentTo(expected: List<Bestillingsbatch>) {
    this.size shouldBe expected.size
    this.shouldContainAllIgnoringFields(
        expected,
        Bestillingsbatch::oppdatert,
        Bestillingsbatch::opprettet,
        Bestillingsbatch::id,
        Bestillingsbatch::dataSendt,
    )
}
