package no.nav.sokos.skattekort.module.skattekort

import java.time.LocalDateTime

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.time.withConstantNow
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk

import no.nav.sokos.skattekort.TestUtil.tx
import no.nav.sokos.skattekort.config.PropertiesConfig
import no.nav.sokos.skattekort.infrastructure.DbListener
import no.nav.sokos.skattekort.infrastructure.FakeUnleashIntegration
import no.nav.sokos.skattekort.module.person.PersonId
import no.nav.sokos.skattekort.module.person.PersonService
import no.nav.sokos.skattekort.module.skattekort.ResultatForSkattekort.SkattekortopplysningerOK
import no.nav.sokos.skattekort.skatteetaten.SkatteetatenClient

class BestillingServiceOppdaterteSkattekortTest :
    FunSpec(
        {
            extensions(DbListener)

            val skatteetatenClient = mockk<SkatteetatenClient>()

            val bestillingService: BestillingService by lazy {
                BestillingService(
                    DbListener.dataSource,
                    skatteetatenClient,
                    PersonService(DbListener.dataSource),
                    FakeUnleashIntegration(),
                    PropertiesConfig.ApplicationProperties("", PropertiesConfig.Environment.TEST, false, false, "", "", ""),
                )
            }

            test("Når vi gjør et kall med tom database i midten av året skal det opprettes én batch") {
                withConstantNow(LocalDateTime.parse("2025-04-12T00:00:00")) {
                    coEvery { skatteetatenClient.bestillSkattekort(any()) } returns
                        toBestillSkattekortResponse(
                            """
                            {
                              "dialogreferanse": "some-dialog-ref",
                              "bestillingsreferanse": "some-bestillings-ref"
                            }
                            """.trimIndent(),
                        )
                    databaseHas(
                        aPerson(1L, "01010100001"),
                        aPerson(2L, "02020200002"),
                        aPerson(3L, "03030300003"),
                    )

                    bestillingService.hentOppdaterteSkattekort()

                    val batches: List<BestillingBatch> = tx(BestillingBatchRepository::list)

                    assertSoftly {
                        batches shouldNotBeNull {
                            size shouldBe 1
                            first() shouldNotBeNull {
                                status shouldBe BestillingBatchStatus.Ny.value
                                type shouldBe "OPPDATERING"
                                bestillingsreferanse shouldBe "some-bestillings-ref"
                            }
                        }
                    }
                }
            }

            test("Når vi gjør et kall med tom database i slutten av desember skal det opprettes to batcher") {
                withConstantNow(LocalDateTime.parse("2025-12-20T00:00:00")) {
                    coEvery { skatteetatenClient.bestillSkattekort(any()) } returnsMany
                        listOf(
                            toBestillSkattekortResponse(
                                """
                                {
                                  "dialogreferanse": "some-dialog-ref1",
                                  "bestillingsreferanse": "some-bestillings-ref1"
                                }
                                """.trimIndent(),
                            ),
                            toBestillSkattekortResponse(
                                """
                                {
                                  "dialogreferanse": "some-dialog-ref2",
                                  "bestillingsreferanse": "some-bestillings-ref2"
                                }
                                """.trimIndent(),
                            ),
                        )
                    databaseHas(
                        aPerson(1L, "01010100001"),
                        aPerson(2L, "02020200002"),
                        aPerson(3L, "03030300003"),
                    )

                    bestillingService.hentOppdaterteSkattekort()

                    val batches: List<BestillingBatch> = tx(BestillingBatchRepository::list)

                    assertSoftly {
                        batches shouldNotBeNull {
                            size shouldBe 2
                            first() shouldNotBeNull {
                                status shouldBe BestillingBatchStatus.Ny.value

                                type shouldBe "OPPDATERING"
                                bestillingsreferanse shouldBe "some-bestillings-ref1"
                            }
                            elementAt(1) shouldNotBeNull {
                                status shouldBe BestillingBatchStatus.Ny.value
                                type shouldBe "OPPDATERING"
                                bestillingsreferanse shouldBe "some-bestillings-ref2"
                            }
                        }
                    }
                }
            }

            test("Når vi gjør et kall med batcher i databasen skal det hentes skattekort") {
                withConstantNow(LocalDateTime.parse("2025-12-20T00:00:00")) {
                    coEvery { skatteetatenClient.hentSkattekort(any()) } returns
                        aHentSkattekortResponse(
                            aSkattekortFor("01010100001", 10001),
                        )
                    databaseHas(
                        aPerson(1L, "01010100001"),
                        aPerson(2L, "02020200002"),
                        aPerson(3L, "03030300003"),
                        aBestillingsBatch(1L, "REF0001", "NY", "OPPDATERING"),
                    )

                    bestillingService.hentOppdaterteSkattekort()

                    val batches: List<BestillingBatch> = tx(BestillingBatchRepository::list)
                    val skattekort: List<Skattekort> = tx { SkattekortRepository.findAllByPersonId(it, PersonId(1), 2025) }

                    assertSoftly {
                        batches shouldNotBeNull {
                            size shouldBe 1
                            first() shouldNotBeNull {
                                status shouldBe BestillingBatchStatus.Ferdig.value
                                type shouldBe "OPPDATERING"
                                bestillingsreferanse shouldBe "REF0001"
                            }
                        }
                        skattekort shouldNotBeNull {
                            size shouldBe 1
                            first() shouldNotBeNull {
                                identifikator shouldBe "10001"
                                resultatForSkattekort shouldBe SkattekortopplysningerOK
                                forskuddstrekkList shouldNotBeNull {
                                    size shouldBe 2
                                }
                            }
                        }
                    }
                }
            }
        },
    )
