package no.nav.sokos.skattekort.skattekortbestilling

import java.time.LocalDateTime

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.time.withConstantNow
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk

import no.nav.sokos.skattekort.infrastructure.UnleashIntegration
import no.nav.sokos.skattekort.infrastructure.skatteetaten.SkatteetatenClient
import no.nav.sokos.skattekort.listener.DbListener
import no.nav.sokos.skattekort.skattekort.ResponseStatus
import no.nav.sokos.skattekort.skattekort.aBestillingsBatch
import no.nav.sokos.skattekort.skattekort.aHentSkattekortResponse
import no.nav.sokos.skattekort.skattekort.aPerson
import no.nav.sokos.skattekort.skattekort.afoedselsnummer
import no.nav.sokos.skattekort.skattekort.databaseHas
import no.nav.sokos.skattekort.skattekorthenting.BestillingService
import no.nav.sokos.skattekort.utils.DBTestUtils
import no.nav.sokos.skattekort.utils.TestUtils

class BestillingServiceOppdaterteSkattekortTest :
    FunSpec(
        {
            extensions(DbListener)

            val skatteetatenClient = mockk<SkatteetatenClient>()
            val bestillingService: BestillingService by lazy {
                BestillingService(
                    dataSource = DbListener.dataSource,
                    skatteetatenClient = skatteetatenClient,
                    featureToggles = UnleashIntegration(),
                )
            }

//            test("Når vi gjør et kall med tom database i midten av året skal det opprettes én batch") {
//                withConstantNow(LocalDateTime.parse("2025-04-12T00:00:00")) {
//                    coEvery { skatteetatenClient.bestillSkattekort(any()) } returns okBestillSkattekortResponse("some-bestillings-ref")
//                    databaseHas(
//                        aPerson(1L),
//                        afoedselsnummer(1L, "01010100001"),
//                        aPerson(2L),
//                        afoedselsnummer(2L, "02020200002"),
//                        aPerson(3L),
//                        afoedselsnummer(3L, "03030300003"),
//                    )
//
//                    bestillingService.hentBestillingsbatcher(BestillingsbatchType.OPPDATERING)
//
//                    val batches: List<BestillingBatch> = tx(BestillingBatchRepository::list)
//
//                    batches.shouldBeFunctionallyEquivalentTo(listOf(
//                            aBatch(id = 1L, bestillingsreferanse = "some-bestillings-ref", status = BestillingBatchStatus.Ny, type = "OPPDATERING")
//                    ))
//                }
//            }

//            test("Når vi gjør et kall med tom database i slutten av desember skal det opprettes to batcher") {
//                withConstantNow(LocalDateTime.parse("2025-12-20T00:00:00")) {
//                    coEvery { skatteetatenClient.bestillSkattekort(any()) } returnsMany
//                        listOf(
//                            okBestillSkattekortResponse("some-bestillings-ref1"),
//                            okBestillSkattekortResponse("some-bestillings-ref2"),
//                        )
//                    databaseHas(
//                        aPerson(1L),
//                        afoedselsnummer(1L, "01010100001"),
//                        aPerson(2L),
//                        afoedselsnummer(2L, "02020200002"),
//                        aPerson(3L),
//                        afoedselsnummer(3L, "03030300003"),
//                    )
//
//                    bestillingService.hentBestillingsbatcher(BestillingsbatchType.OPPDATERING)
//
//                    val batches: List<BestillingBatch> = tx(BestillingBatchRepository::list)
//
//                    assertSoftly {
//                        batches shouldNotBeNull {
//                            size shouldBe 2
//                            first() shouldNotBeNull {
//                                status shouldBe BestillingBatchStatus.Ny.value
//
//                                type shouldBe "OPPDATERING"
//                                bestillingsreferanse shouldBe "some-bestillings-ref1"
//                            }
//                            elementAt(1) shouldNotBeNull {
//                                status shouldBe BestillingBatchStatus.Ny.value
//                                type shouldBe "OPPDATERING"
//                                bestillingsreferanse shouldBe "some-bestillings-ref2"
//                            }
//                        }
//                    }
//                }
//            }

//            test("Når vi gjør et kall med batcher i databasen skal det hentes skattekort") {
//                withConstantNow(LocalDateTime.parse("2025-12-20T00:00:00")) {
//                    coEvery { skatteetatenClient.hentSkattekort(any()) } returns
//                        aHentSkattekortResponse(
//                            aSkattekortFor("01010100001", 10001),
//                        )
//                    databaseHas(
//                        aPerson(1L),
//                        afoedselsnummer(1L, "01010100001"),
//                        aPerson(2L),
//                        afoedselsnummer(2L, "02020200002"),
//                        aPerson(3L),
//                        afoedselsnummer(3L, "03030300003"),
//                        aBestillingsBatch(1L, "REF0001", "NY", "OPPDATERING"),
//                    )
//
//                    bestillingService.hentBestillingsbatcher(BestillingsbatchType.OPPDATERING)
//
//                    val batches: List<BestillingBatch> = tx(BestillingBatchRepository::list)
//                    val skattekort: List<Skattekort> = tx { SkattekortRepository.findAllByPersonId(it, PersonId(1), 2025, adminRole = false) }
//
//                    assertSoftly {
//                        batches shouldNotBeNull {
//                            size shouldBe 1
//                            first() shouldNotBeNull {
//                                status shouldBe BestillingBatchStatus.Ferdig.value
//                                type shouldBe "OPPDATERING"
//                                bestillingsreferanse shouldBe "REF0001"
//                            }
//                        }
//                        skattekort shouldNotBeNull {
//                            size shouldBe 1
//                            first() shouldNotBeNull {
//                                identifikator shouldBe "10001"
//                                resultatForSkattekort shouldBe SkattekortopplysningerOK
//                                forskuddstrekkList shouldNotBeNull {
//                                    size shouldBe 2
//                                }
//                            }
//                        }
//                    }
//                }
//            }

//            test("Logger som feil for ukjente personer fra henting av skattekort") {
//                withConstantNow(LocalDateTime.parse("2025-12-20T00:00:00")) {
//                    coEvery { skatteetatenClient.hentSkattekort(any()) } returns
//                        aHentSkattekortResponse(
//                            aSkattekortFor("0101010000X", 10007),
//                        )
//                    databaseHas(
//                        aPerson(1L),
//                        afoedselsnummer(1L, "01010100001"),
//                        aPerson(2L),
//                        afoedselsnummer(2L, "02020200002"),
//                        aPerson(3L),
//                        afoedselsnummer(3L, "03030300003"),
//                        aBestillingsBatch(1L, "REF0001", "NY", "OPPDATERING"),
//                    )
//
//                    bestillingService.hentBestillingsbatcher(BestillingsbatchType.OPPDATERING)
//
//                    val person = tx { PersonRepository.findPersonByFnr(it, Personidentifikator("0101010000X")) }
//                    val batches: List<BestillingBatch> = tx(BestillingBatchRepository::list)
//
//                    assertSoftly {
//                        person shouldBe null
//                        batches shouldNotBeNull {
//                            size shouldBe 1
//                            first() shouldNotBeNull {
//                                status shouldBe BestillingBatchStatus.Ferdig.value
//                                type shouldBe "OPPDATERING"
//                                bestillingsreferanse shouldBe "REF0001"
//                            }
//                        }
//                    }
//                }
//            }

            test("Henting av oppdaterte skattekort uten oppdateringer skal fungere") {
                withConstantNow(LocalDateTime.parse("2025-12-20T00:00:00")) {
                    coEvery { skatteetatenClient.hentSkattekort(any()) } returns
                        aHentSkattekortResponse(response = ResponseStatus.INGEN_ENDRINGER)
                    databaseHas(
                        aPerson(1L),
                        afoedselsnummer(1L, "01010100001"),
                        aPerson(2L),
                        afoedselsnummer(2L, "02020200002"),
                        aPerson(3L),
                        afoedselsnummer(3L, "03030300003"),
                        aBestillingsBatch(1L, "REF0001", "NY", "OPPDATERING"),
                    )

                    bestillingService.hentBestillingsbatcher(BestillingsbatchType.OPPDATERING)

                    val batches: List<Bestillingsbatch> = TestUtils.tx(DBTestUtils::getAllBestillingsbatch)

                    assertSoftly {
                        batches shouldNotBeNull {
                            size shouldBe 1
                            first() shouldNotBeNull {
                                status shouldBe BestillingsbatchStatus.FERDIG.value
                                type shouldBe "OPPDATERING"
                                bestillingsreferanse shouldBe "REF0001"
                            }
                        }
                    }
                }
            }

            test("Henting av oppdaterte skattekort med ugyldig inntektsår skal feile") {
                withConstantNow(LocalDateTime.parse("2025-12-20T00:00:00")) {
                    coEvery { skatteetatenClient.hentSkattekort(any()) } returns
                        aHentSkattekortResponse(response = ResponseStatus.UGYLDIG_INNTEKTSAAR)
                    databaseHas(
                        aPerson(1L),
                        afoedselsnummer(1L, "01010100001"),
                        aPerson(2L),
                        afoedselsnummer(2L, "02020200002"),
                        aPerson(3L),
                        afoedselsnummer(3L, "03030300003"),
                        aBestillingsBatch(1L, "REF0001", "NY", "OPPDATERING"),
                    )

                    bestillingService.hentBestillingsbatcher(BestillingsbatchType.OPPDATERING)

                    val batches: List<Bestillingsbatch> = TestUtils.tx(DBTestUtils::getAllBestillingsbatch)

                    assertSoftly {
                        batches shouldNotBeNull {
                            size shouldBe 1
                            first() shouldNotBeNull {
                                status shouldBe BestillingsbatchStatus.FEILET.value
                                type shouldBe "OPPDATERING"
                                bestillingsreferanse shouldBe "REF0001"
                            }
                        }
                    }
                }
            }
        },
    )
