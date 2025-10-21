package no.nav.sokos.skattekort.module.skattekort

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.coEvery
import io.mockk.mockk
import kotliquery.queryOf

import no.nav.sokos.skattekort.listener.DbListener
import no.nav.sokos.skattekort.skatteetaten.SkatteetatenBestillSkattekortResponse
import no.nav.sokos.skattekort.skatteetaten.SkatteetatenClient
import no.nav.sokos.skattekort.util.SQLUtils.transaction

class BestillingsServiceTest :
    FunSpec({
        extensions(DbListener)

        val skatteetatenClient = mockk<SkatteetatenClient>()

        val bestillingsService: BestillingsService by lazy {
            BestillingsService(DbListener.dataSource, skatteetatenClient)
        }

        test("vi kan opprette bestillingsbatch og knytte bestillinger til batch") {
            val bestillingsreferanse = "some-bestillings-ref"

            coEvery { skatteetatenClient.bestillSkattekort(any()) } returns
                SkatteetatenBestillSkattekortResponse(
                    dialogreferanse = "some-dialog-ref",
                    bestillingsreferanse = bestillingsreferanse,
                )

            // Sett inn bestillinger uten bestillingsbatch.
            DbListener.loadDataSet("database/person/persondata.sql")
            val fnr1 = "12345678901"
            val fnr2 = "12345678902"
            val fnr3 = "12345678903"
            DbListener.dataSource.transaction { session ->
                session.run(
                    queryOf(
                        """
                        INSERT INTO bestillinger(person_id, fnr, inntektsaar, bestillingsbatch_id)
                        VALUES (1, '$fnr1', 2025, NULL),
                               (2, '$fnr2', 2025, NULL),
                               (3, '$fnr3', 2025, NULL);
                        """.trimIndent(),
                    ).asExecute,
                )
            }

            bestillingsService.opprettBestillingsbatch()

            val bestillings: List<Bestilling> =
                DbListener.dataSource.transaction { session ->
                    BestillingRepository.getAllBestilling(session)
                }

            val batches: List<BestillingBatch> =
                DbListener.dataSource.transaction { session ->
                    BestillingBatchRepository.list(session)
                }

            assertSoftly {
                batches.size shouldBe 1
                batches.first().bestillingsreferanse shouldBe bestillingsreferanse
                batches.first().dataSendt shouldContain fnr1
                batches.first().dataSendt shouldContain fnr2
                batches.first().dataSendt shouldContain fnr3

                bestillings.count { it.bestillingsbatchId != null } shouldBe 3
                bestillings.all { it.bestillingsbatchId == batches.first().id } shouldBe true
            }
        }
    })
