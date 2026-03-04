package no.nav.sokos.skattekort.skattekortbestilling

import java.time.LocalDateTime

import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.time.withConstantNow
import io.kotest.matchers.collections.shouldContainAllIgnoringFields
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
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
import no.nav.sokos.skattekort.skattekort.aBestilling
import no.nav.sokos.skattekort.skattekort.aPerson
import no.nav.sokos.skattekort.skattekort.afoedselsnummer
import no.nav.sokos.skattekort.skattekort.databaseHas
import no.nav.sokos.skattekort.skattekortbestilling.BestillingsbatchStatus.NY
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

        test("Hvis det er bestillinger for neste år, ikke plukk opp før 15.12.") {
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
                    listOf(
                        no.nav.sokos.skattekort.skattekort
                            .aBatch(id = 1L, status = NY, type = "oppdatering", bestillingsreferanse = "ref1"),
                    ),
                    Bestillingsbatch::oppdatert,
                    Bestillingsbatch::opprettet,
                    Bestillingsbatch::dataSendt,
                    Bestillingsbatch::type,
                )
                batches.first().dataSendt shouldNotBeNull {
                    this shouldContain "01010100001"
                    this shouldNotContain "02020200002"
                    this shouldNotContain "03030300003"
                }

                bestillings.size shouldBe 3
                bestillings.shouldContainAllIgnoringFields(
                    listOf(
                        bestilling(1L, "01010100001", 2025, batchId = 1L),
                        bestilling(2L, "02020200002", 2026, batchId = null),
                        bestilling(3L, "03030300003", 2026, batchId = null),
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
                        no.nav.sokos.skattekort.skattekort
                            .aBatch(id = 1L, status = NY, type = BESTILLING, bestillingsreferanse = "ref1"),
                        no.nav.sokos.skattekort.skattekort
                            .aBatch(id = 2L, status = NY, type = BESTILLING, bestillingsreferanse = "ref2"),
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
                        bestilling(pid = 1L, fnr = "01010100001", year = 2025, batchId = 1L),
                        bestilling(pid = 2L, fnr = "02020200002", year = 2026, batchId = 2L),
                        bestilling(pid = 3L, fnr = "03030300003", year = 2026, batchId = 2L),
                    ),
                    Bestilling::oppdatert,
                    Bestilling::id,
                )
            }
        }
    })

private fun bestilling(
    pid: Long,
    fnr: String,
    year: Int,
    batchId: Long?,
): Bestilling =
    Bestilling(
        personId = PersonId(pid),
        fnr = Personidentifikator(fnr),
        inntektsaar = year,
        bestillingsbatchId =
            batchId?.let(
                ::BestillingsbatchId,
            ),
    )
