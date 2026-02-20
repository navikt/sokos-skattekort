package no.nav.sokos.skattekort.module.skattekort

import java.time.LocalDateTime

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.time.withConstantNow
import io.kotest.inspectors.forExactly
import io.kotest.inspectors.forOne
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.coEvery
import io.mockk.mockk

import no.nav.sokos.skattekort.infrastructure.UnleashIntegration
import no.nav.sokos.skattekort.listener.DbListener
import no.nav.sokos.skattekort.skatteetaten.SkatteetatenClient
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
                toBestillSkattekortResponse(
                    """
                    {
                      "dialogreferanse": "first-dialog-ref",
                      "bestillingsreferanse": "first-bestillings-ref"
                    }
                    """.trimIndent(),
                ) andThen
                toBestillSkattekortResponse(
                    """
                    {
                      "dialogreferanse": "second-dialog-ref",
                      "bestillingsreferanse": "second-bestillings-ref"
                    }
                    """.trimIndent(),
                )
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

                assertSoftly("Før 15. desember") {
                    batches shouldNotBeNull {
                        size shouldBe 1
                        first() shouldNotBeNull {
                            status shouldBe BestillingBatchStatus.Ny.value
                            bestillingsreferanse shouldBe "first-bestillings-ref"
                            dataSendt shouldNotBeNull {
                                shouldContain("01010100001")
                                shouldNotContain("02020200002")
                                shouldNotContain("03030300003")
                            }
                        }
                    }

                    bestillings shouldNotBeNull {
                        size shouldBe 3
                        forOne {
                            it.id shouldNotBeNull { id shouldBe 1L }
                            it.inntektsaar shouldBe 2025
                            it.bestillingsbatchId shouldBe batches.first().id
                        }
                        forExactly(2) {
                            it.inntektsaar shouldBe 2026
                            it.bestillingsbatchId shouldBe null
                        }
                    }
                }
            }
            withConstantNow(LocalDateTime.parse("2025-12-15T00:00:00")) {
                bestillingBatchService.opprettBestillingsbatch()

                val bestillings: List<Bestilling> = tx(BestillingRepository::getBestillingsKandidaterForBatch)
                val batches: List<BestillingBatch> = tx(BestillingBatchRepository::list)

                assertSoftly("Etter 15.desember") {
                    batches shouldNotBeNull {
                        size shouldBe 2
                        first() shouldNotBeNull {
                            id shouldNotBeNull { id shouldBe 1L }
                            status shouldBe BestillingBatchStatus.Ny.value
                            bestillingsreferanse shouldBe "first-bestillings-ref"
                            dataSendt shouldNotBeNull {
                                shouldNotContain("01010100001")
                                shouldNotContain("02020200002")
                                shouldNotContain("03030300003")
                            }
                        }
                        last() shouldNotBeNull {
                            id shouldNotBeNull { id shouldBe 2L }
                            status shouldBe BestillingBatchStatus.Ny.value
                            bestillingsreferanse shouldBe "second-bestillings-ref"
                            dataSendt shouldNotBeNull {
                                shouldNotContain("01010100001")
                                shouldContain("02020200002")
                                shouldContain("03030300003")
                            }
                        }
                    }

                    bestillings shouldNotBeNull {
                        size shouldBe 3
                        forOne {
                            it.id shouldNotBeNull { id shouldBe 1L }
                            it.inntektsaar shouldBe 2025
                            it.bestillingsbatchId shouldNotBeNull { id shouldBe 1L }
                        }
                        forExactly(2) {
                            it.inntektsaar shouldBe 2026
                            it.bestillingsbatchId shouldNotBeNull { id shouldBe 2L }
                        }
                    }
                }
            }
        }
    })
