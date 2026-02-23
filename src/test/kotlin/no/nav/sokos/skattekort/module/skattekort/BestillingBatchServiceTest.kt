package no.nav.sokos.skattekort.module.skattekort

import java.time.LocalDateTime

import kotlin.time.Instant

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
import no.nav.sokos.skattekort.listener.DbListener
import no.nav.sokos.skattekort.module.person.PersonId
import no.nav.sokos.skattekort.module.person.Personidentifikator
import no.nav.sokos.skattekort.module.skattekort.BestillingBatchStatus.Ny
import no.nav.sokos.skattekort.skatteetaten.SkatteetatenClient
import no.nav.sokos.skattekort.skatteetaten.bestillskattekort.BestillSkattekortResponse
import no.nav.sokos.skattekort.utils.TestUtils.tx

class BestillingBatchServiceTest :
    FunSpec({
        extensions(DbListener)

        val skatteetatenClient = mockk<SkatteetatenClient>()

        val bestillingBatchService: BestillingBatchService by lazy {
            BestillingBatchService(
                dataSource = DbListener.dataSource,
                skatteetatenClient = skatteetatenClient,
                featureToggles = UnleashIntegration(),
            )
        }

        test("Hvis det er bestillinger for neste år, ikke plukk opp før 15.12.") {
            coEvery { skatteetatenClient.bestillSkattekort(any()) } returns
                ok("ref1") andThen ok("ref2")
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

            withConstantNow(LocalDateTime.parse("2025-12-14T00:00:00")) {
                // Kaller to ganger for å sjekke at den ikke plukker opp 2026 på andre kall
                bestillingBatchService.opprettBestillingsbatch()
                bestillingBatchService.opprettBestillingsbatch()

                val bestillings: List<Bestilling> = tx(BestillingRepository::getBestillingsKandidaterForBatch)
                val batches: List<BestillingBatch> = tx(BestillingBatchRepository::list)

                batches.size shouldBe 1
                batches.shouldContainAllIgnoringFields(
                    listOf(
                        batch(id = 1L, status = Ny, type = "oppdatering", bestillingsreferanse = "ref1"),
                    ),
                    BestillingBatch::oppdatert,
                    BestillingBatch::opprettet,
                    BestillingBatch::dataSendt,
                    BestillingBatch::type,
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
                bestillingBatchService.opprettBestillingsbatch()

                val bestillings: List<Bestilling> = tx(BestillingRepository::getBestillingsKandidaterForBatch)
                val batches: List<BestillingBatch> = tx(BestillingBatchRepository::list)

                batches.size shouldBe 2
                batches.shouldContainAllIgnoringFields(
                    listOf(
                        batch(id = 1L, status = Ny, type = BESTILLING, bestillingsreferanse = "ref1"),
                        batch(id = 2L, status = Ny, type = BESTILLING, bestillingsreferanse = "ref2"),
                    ),
                    BestillingBatch::oppdatert,
                    BestillingBatch::opprettet,
                    BestillingBatch::id,
                    BestillingBatch::dataSendt,
                )
                batches.first { it.id?.id == 1L }.dataSendt shouldNotBeNull {
                    this shouldContain "01010100003"
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

const val BESTILLING = "BESTILLING"

private fun bestilling(
    pid: Long,
    fnr: String,
    year: Int,
    batchId: Long?,
): Bestilling = Bestilling(personId = PersonId(pid), fnr = Personidentifikator(fnr), inntektsaar = year, bestillingsbatchId = batchId?.let(::BestillingsbatchId))

private fun batch(
    id: Long,
    status: BestillingBatchStatus,
    type: String,
    bestillingsreferanse: String,
): BestillingBatch =
    BestillingBatch(
        id = BestillingsbatchId(id),
        status = status.value,
        type = type,
        bestillingsreferanse = bestillingsreferanse,
        oppdatert = Instant.DISTANT_PAST,
        opprettet = Instant.DISTANT_PAST,
        dataSendt = "",
    )

private fun ok(ref: String): BestillSkattekortResponse =
    toBestillSkattekortResponse(
        """
        {
          "dialogreferanse": "any-dialog-ref",
          "bestillingsreferanse": "$ref"
        }
        """.trimIndent(),
    )
