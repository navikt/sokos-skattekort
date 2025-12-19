package no.nav.sokos.skattekort.module.skattekort

import java.math.BigDecimal.valueOf
import java.math.RoundingMode
import java.time.LocalDateTime

import kotlin.time.ExperimentalTime

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.time.withConstantNow
import io.kotest.inspectors.forAll
import io.kotest.inspectors.forExactly
import io.kotest.inspectors.forOne
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldContainAllIgnoringFields
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.coEvery
import io.mockk.mockk

import no.nav.sokos.skattekort.config.PropertiesConfig
import no.nav.sokos.skattekort.config.PropertiesConfig.Environment
import no.nav.sokos.skattekort.infrastructure.DbListener
import no.nav.sokos.skattekort.infrastructure.FakeUnleashIntegration
import no.nav.sokos.skattekort.module.forespoersel.Forsystem
import no.nav.sokos.skattekort.module.person.Audit
import no.nav.sokos.skattekort.module.person.AuditRepository
import no.nav.sokos.skattekort.module.person.AuditTag
import no.nav.sokos.skattekort.module.person.Person
import no.nav.sokos.skattekort.module.person.PersonId
import no.nav.sokos.skattekort.module.person.PersonRepository
import no.nav.sokos.skattekort.module.person.Personidentifikator
import no.nav.sokos.skattekort.module.skattekort.ResultatForSkattekort.IkkeSkattekort
import no.nav.sokos.skattekort.module.skattekort.ResultatForSkattekort.IkkeTrekkplikt
import no.nav.sokos.skattekort.module.skattekort.ResultatForSkattekort.SkattekortopplysningerOK
import no.nav.sokos.skattekort.module.skattekort.Trekkode.LOENN_FRA_BIARBEIDSGIVER
import no.nav.sokos.skattekort.module.skattekort.Trekkode.LOENN_FRA_HOVEDARBEIDSGIVER
import no.nav.sokos.skattekort.module.skattekort.Trekkode.LOENN_FRA_NAV
import no.nav.sokos.skattekort.module.skattekort.Trekkode.PENSJON_FRA_NAV
import no.nav.sokos.skattekort.module.skattekort.Trekkode.UFOERETRYGD_FRA_NAV
import no.nav.sokos.skattekort.module.skattekort.Trekkode.UFOEREYTELSER_FRA_ANDRE
import no.nav.sokos.skattekort.module.utsending.Utsending
import no.nav.sokos.skattekort.module.utsending.UtsendingId
import no.nav.sokos.skattekort.module.utsending.UtsendingRepository
import no.nav.sokos.skattekort.skatteetaten.SkatteetatenClient
import no.nav.sokos.skattekort.skatteetaten.hentskattekort.Forskuddstrekk
import no.nav.sokos.skattekort.skatteetaten.hentskattekort.HentSkattekortResponse
import no.nav.sokos.skattekort.skatteetaten.hentskattekort.Trekkprosent
import no.nav.sokos.skattekort.utils.TestUtils.tx

@OptIn(ExperimentalTime::class)
class BestillingServiceTest :
    FunSpec({
        extensions(DbListener)

        val skatteetatenClient = mockk<SkatteetatenClient>()

        val bestillingService: BestillingService by lazy {
            BestillingService(
                DbListener.dataSource,
                skatteetatenClient,
                FakeUnleashIntegration(),
                PropertiesConfig.ApplicationProperties("", Environment.TEST, false, "", "", ""),
            )
        }

        test("vi kan opprette bestillingsbatch og knytte bestillinger til batch") {
            withConstantNow(LocalDateTime.parse("2025-12-15T00:00:00")) {
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
                    aBestilling(1L, "01010100001", 2026, null),
                    aBestilling(2L, "02020200002", 2026, null),
                    aBestilling(3L, "03030300003", 2026, null),
                )

                bestillingService.opprettBestillingsbatch()

                val bestillings: List<Bestilling> = tx(BestillingRepository::getBestillingsKandidaterForBatch)
                val batches: List<BestillingBatch> = tx(BestillingBatchRepository::list)

                assertSoftly {
                    batches shouldNotBeNull {
                        size shouldBe 1
                        first() shouldNotBeNull {
                            status shouldBe BestillingBatchStatus.Ny.value
                            bestillingsreferanse shouldBe "some-bestillings-ref"
                            dataSendt shouldNotBeNull {
                                shouldContain("01010100001")
                                shouldContain("02020200002")
                                shouldContain("03030300003")
                            }
                        }
                    }

                    bestillings shouldNotBeNull {
                        size shouldBe 3
                        forAll { it.bestillingsbatchId shouldBe batches.first().id }
                    }
                }
            }
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
                aPerson(1L, "01010100001"),
                aPerson(2L, "02020200002"),
                aPerson(3L, "03030300003"),
                aBestilling(1L, "01010100001", 2025, null),
                aBestilling(2L, "02020200002", 2026, null),
                aBestilling(3L, "03030300003", 2026, null),
            )

            withConstantNow(LocalDateTime.parse("2025-12-14T00:00:00")) {
                // Kaller to ganger for å sjekke at den ikke plukker opp 2026 på andre kall
                bestillingService.opprettBestillingsbatch()
                bestillingService.opprettBestillingsbatch()

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
                bestillingService.opprettBestillingsbatch()

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

        test("henter skattekort enkleste scenario") {
            coEvery { skatteetatenClient.hentSkattekort(any(), any()) } returns
                aHentSkattekortResponse(
                    aSkattekortFor("01010100001", 10001),
                )

            databaseHas(
                aPerson(1L, "01010100001"),
                anAbonnement(1L, personId = 1L, inntektsaar = 2025),
                aBestillingsBatch(1, "ref1", BestillingBatchStatus.Ny.value),
                aBestilling(1L, "01010100001", 2025, 1L),
            )

            bestillingService.hentSkattekort()

            val updatedBatches: List<BestillingBatch> = tx(BestillingBatchRepository::list)
            val skattekort: List<Skattekort> = tx { SkattekortRepository.findAllByPersonId(it, PersonId(1), 2025, adminRole = false) }
            val bestillingsAfter: List<Bestilling> = tx(BestillingRepository::getBestillingsKandidaterForBatch)
            val utsendingerAfter: List<Utsending> = tx(UtsendingRepository::getAllUtsendinger)

            assertSoftly {
                updatedBatches shouldNotBeNull {
                    size shouldBe 1
                    first() shouldNotBeNull {
                        status shouldBe BestillingBatchStatus.Ferdig.value
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

                bestillingsAfter shouldNotBeNull {
                    size shouldBe 0
                }

                utsendingerAfter.size shouldBe 1
            }
        }

        test("henter skattekort, ingen endring-respons") {
            coEvery { skatteetatenClient.hentSkattekort(any(), any()) } returns
                aHentSkattekortResponse(
                    response = ResponseStatus.INGEN_ENDRINGER,
                )

            databaseHas(
                aPerson(1L, "01010100001"),
                anAbonnement(1L, personId = 1L, inntektsaar = 2025),
                aBestillingsBatch(1, "ref1", BestillingBatchStatus.Ny.value),
                aBestilling(1L, "01010100001", 2025, 1L),
            )

            bestillingService.hentSkattekort()

            val updatedBatches: List<BestillingBatch> = tx(BestillingBatchRepository::list)

            assertSoftly {
                updatedBatches shouldNotBeNull {
                    size shouldBe 1
                    first() shouldNotBeNull {
                        status shouldBe BestillingBatchStatus.Ferdig.value
                    }
                }
            }
        }

        test("henter skattekort, ugyldig inntektsaar returneres") {
            coEvery { skatteetatenClient.hentSkattekort(any(), any()) } returns
                aHentSkattekortResponse(
                    response = ResponseStatus.UGYLDIG_INNTEKTSAAR,
                )

            databaseHas(
                aPerson(1L, "01010100001"),
                anAbonnement(1L, personId = 1L, inntektsaar = 2025),
                aBestillingsBatch(1, "ref1", BestillingBatchStatus.Ny.value),
                aBestilling(1L, "01010100001", 2025, 1L),
            )

            bestillingService.hentSkattekort()

            val updatedBatches: List<BestillingBatch> = tx(BestillingBatchRepository::list)

            assertSoftly {
                updatedBatches shouldNotBeNull {
                    size shouldBe 1
                    first() shouldNotBeNull {
                        status shouldBe BestillingBatchStatus.Feilet.value
                    }
                }
            }
        }

        test("henter skattekort reell response") {
            coEvery { skatteetatenClient.hentSkattekort(any(), "BR1337") } returns aHentSkattekortResponseFromFile("src/test/resources/skatteetaten/hentSkattekort/skattekortopplysningerOK.json")

            databaseHas(
                aPerson(1L, "12345678901"),
                anAbonnement(1L, personId = 1L, inntektsaar = 2025),
                aBestillingsBatch(1, "BR1337", BestillingBatchStatus.Ny.value),
                aBestilling(1L, "12345678901", 2025, 1L),
            )

            bestillingService.hentSkattekort()

            val skattekort: List<Skattekort> = tx { SkattekortRepository.findAllByPersonId(it, PersonId(1), 2025, adminRole = true) }

            assertSoftly {
                skattekort shouldNotBeNull {
                    size shouldBe 2
                    last() shouldNotBeNull {
                        identifikator shouldBe "54407"
                        kilde shouldBe SkattekortKilde.SKATTEETATEN.value
                        resultatForSkattekort shouldBe SkattekortopplysningerOK
                        withClue("Should return forskuddstrekk from response") {
                            forskuddstrekkList shouldContainExactly
                                listOf(
                                    aForskuddstrekk("Tabellkort", LOENN_FRA_HOVEDARBEIDSGIVER, tabellNummer = "8140", prosentSats = 43.0, antMndForTrekk = 10.5),
                                    aForskuddstrekk("Prosentkort", LOENN_FRA_BIARBEIDSGIVER, prosentSats = 43.0, antMndForTrekk = null),
                                    aForskuddstrekk("Prosentkort", LOENN_FRA_NAV, prosentSats = 43.0, antMndForTrekk = null),
                                    aForskuddstrekk("Prosentkort", UFOERETRYGD_FRA_NAV, prosentSats = 43.0, antMndForTrekk = null),
                                    aForskuddstrekk("Prosentkort", UFOEREYTELSER_FRA_ANDRE, prosentSats = 43.0, antMndForTrekk = null),
                                )
                        }
                    }
                    first() shouldNotBeNull {
                        identifikator shouldBe null
                        kilde shouldBe SkattekortKilde.SYNTETISERT.value
                        generertFra shouldBe last().id
                        resultatForSkattekort shouldBe SkattekortopplysningerOK
                        withClue("Should generate forskuddstrekk for svalbard") {
                            forskuddstrekkList shouldContainExactly
                                listOf(
                                    aForskuddstrekk("Prosentkort", LOENN_FRA_NAV, 15.70),
                                    aForskuddstrekk("Prosentkort", UFOERETRYGD_FRA_NAV, 15.70),
                                    aForskuddstrekk("Prosentkort", PENSJON_FRA_NAV, 13.10),
                                )
                        }
                    }
                }
            }
        }

        test("skattekort reell response med samme identifikator og ny informasjon") {
            coEvery { skatteetatenClient.hentSkattekort(any(), any()) } returns
                aHentSkattekortResponseFromFile("src/test/resources/skatteetaten/hentSkattekort/skattekortopplysningerOK_pre.json") andThen
                aHentSkattekortResponseFromFile(
                    "src/test/resources/skatteetaten/hentSkattekort/skattekortopplysningerOK.json",
                )

            databaseHas(
                aPerson(1L, "12345678901"),
                anAbonnement(1L, personId = 1L, inntektsaar = 2025),
                aBestillingsBatch(1, "BR1337", BestillingBatchStatus.Ny.value),
                aBestillingsBatch(2, "BR1338", BestillingBatchStatus.Ny.value),
                aBestilling(1L, "12345678901", 2025, 1L),
                aBestilling(1L, "23456789012", 2025, 2L),
            )

            bestillingService.hentSkattekort()

            val updatedBatchesFirstRun: List<BestillingBatch> = tx(BestillingBatchRepository::list)
            val skattekortFirstRun: List<Skattekort> = tx { SkattekortRepository.findAllByPersonId(it, PersonId(1), 2025, adminRole = true) }
            val bestillingsAfterFirstRun: List<Bestilling> = tx(BestillingRepository::getBestillingsKandidaterForBatch)
            val utsendingerAfterFirstRun: List<Utsending> = tx(UtsendingRepository::getAllUtsendinger)

            assertSoftly {
                updatedBatchesFirstRun shouldNotBeNull {
                    size shouldBe 2
                    forOne {
                        it.id!!.id shouldBe 1L
                        it.status shouldBe BestillingBatchStatus.Ferdig.value
                    }
                    forOne {
                        it.id!!.id shouldBe 2L
                        it.status shouldBe BestillingBatchStatus.Ferdig.value
                    }
                }

                skattekortFirstRun shouldNotBeNull {
                    size shouldBe 3
                    last() shouldNotBeNull {
                        identifikator shouldBe "54407"
                        resultatForSkattekort shouldBe SkattekortopplysningerOK
                        forskuddstrekkList shouldNotBeNull {
                            size shouldBe 5
                        }
                    }
                }

                bestillingsAfterFirstRun shouldNotBeNull {
                    size shouldBe 1
                    first() shouldNotBeNull {
                        id shouldBe BestillingId(2L)
                        fnr.value shouldBe "23456789012"
                        inntektsaar shouldBe 2025
                        bestillingsbatchId shouldBe null
                    }
                }

                utsendingerAfterFirstRun.size shouldBe 2
            }
        }

        test("henter skattekort med alle tilleggsopplysninger") {
            coEvery { skatteetatenClient.hentSkattekort(any(), any()) } returns
                aHentSkattekortResponse(
                    anArbeidstaker(
                        resultat = SkattekortopplysningerOK,
                        fnr = "01010100001",
                        inntektsaar = "2025",
                        skattekort =
                            aSkattekort(
                                utstedtDato = "2025-11-01",
                                identifikator = 10001,
                                forskuddstrekk =
                                    listOf(
                                        Forskuddstrekk(
                                            trekkode = UFOERETRYGD_FRA_NAV.value,
                                            trekkprosent = Trekkprosent(valueOf(43)),
                                        ),
                                    ),
                            ),
                        tilleggsopplysninger =
                            listOf(
                                Tilleggsopplysning.fromValue("oppholdPaaSvalbard"),
                                Tilleggsopplysning.fromValue("kildeskattPaaPensjon"),
                                Tilleggsopplysning.fromValue("oppholdITiltakssone"),
                            ),
                    ),
                )

            databaseHas(
                aPerson(1L, "01010100001"),
                anAbonnement(1L, personId = 1L, inntektsaar = 2025),
                aBestillingsBatch(1, "ref1", BestillingBatchStatus.Ny.value),
                aBestilling(1L, "01010100001", 2025, 1L),
            )

            bestillingService.hentSkattekort()

            val skattekort: List<Skattekort> = tx { SkattekortRepository.findAllByPersonId(it, PersonId(1), 2025, adminRole = false) }
            val bestillingsAfter: List<Bestilling> = tx(BestillingRepository::getBestillingsKandidaterForBatch)
            val utsendingerAfter: List<Utsending> = tx(UtsendingRepository::getAllUtsendinger)

            assertSoftly {

                skattekort shouldNotBeNull {
                    size shouldBe 2
                    last() shouldNotBeNull {
                        identifikator shouldBe "10001"
                        resultatForSkattekort shouldBe SkattekortopplysningerOK
                        withClue("Should not alter forskuddstrekk") {
                            forskuddstrekkList shouldNotBeNull {
                                size shouldBe 1
                                shouldContainExactlyInAnyOrder(
                                    listOf(
                                        Prosentkort(
                                            trekkode = UFOERETRYGD_FRA_NAV,
                                            prosentSats = valueOf(43).setScale(2, RoundingMode.HALF_UP),
                                        ),
                                    ),
                                )
                            }
                        }
                        tilleggsopplysningList shouldNotBeNull {
                            shouldContainExactly(
                                Tilleggsopplysning.fromValue("oppholdPaaSvalbard"),
                                Tilleggsopplysning.fromValue("kildeskattPaaPensjon"),
                                Tilleggsopplysning.fromValue("oppholdITiltakssone"),
                            )
                        }
                    }
                    first() shouldNotBeNull {
                        identifikator shouldBe null
                        kilde shouldBe SkattekortKilde.SYNTETISERT.value
                        generertFra shouldBe last().id
                        resultatForSkattekort shouldBe SkattekortopplysningerOK
                        withClue("Should generate forskuddstrekk for svalbard") {
                            forskuddstrekkList shouldContainExactly
                                listOf(
                                    aForskuddstrekk("Prosentkort", LOENN_FRA_NAV, 15.70),
                                    aForskuddstrekk("Prosentkort", UFOERETRYGD_FRA_NAV, 15.70),
                                    aForskuddstrekk("Prosentkort", PENSJON_FRA_NAV, 13.10),
                                )
                        }
                        tilleggsopplysningList shouldNotBeNull {
                            shouldContainExactly(
                                Tilleggsopplysning.fromValue("oppholdPaaSvalbard"),
                                Tilleggsopplysning.fromValue("kildeskattPaaPensjon"),
                                Tilleggsopplysning.fromValue("oppholdITiltakssone"),
                            )
                        }
                    }
                }

                bestillingsAfter shouldNotBeNull {
                    size shouldBe 0
                }

                utsendingerAfter.size shouldBe 1
            }
        }

        test("hent skattekort håndterer alle batcher") {

            coEvery { skatteetatenClient.hentSkattekort(any(), any()) } returns
                aHentSkattekortResponse(
                    aSkattekortFor("01010100001", 10001),
                ) andThen
                aHentSkattekortResponse(
                    aSkattekortFor("02020200002", 20002),
                    aSkattekortFor("03030300003", 30003),
                )

            databaseHas(
                aPerson(1, "01010100001"),
                aPerson(2, "02020200002"),
                aPerson(3, "03030300003"),
                aPerson(4, "04040400004"),
                anAbonnement(1L, personId = 1L, inntektsaar = 2025),
                anAbonnement(2L, personId = 2L, inntektsaar = 2025),
                anAbonnement(3L, personId = 3L, inntektsaar = 2025),
                anAbonnement(4L, personId = 4L, inntektsaar = 2025),
                aBestillingsBatch(1, "ref1", BestillingBatchStatus.Ny.value),
                aBestillingsBatch(2, "ref2", BestillingBatchStatus.Ny.value),
                aBestilling(1L, "01010100001", 2025, 1L),
                aBestilling(2L, "02020200002", 2025, 2L),
                aBestilling(3L, "02020200003", 2025, 2L), // NB: også batch 2
                aBestilling(4L, "04040400004", 2025, null),
            )

            bestillingService.hentSkattekort()
            bestillingService.hentSkattekort()

            val updatedBatches: List<BestillingBatch> = tx(BestillingBatchRepository::list)

            assertSoftly("Etter første kjøring skal alle batchene være Ferdig") {
                updatedBatches shouldNotBeNull {
                    size shouldBe 2
                    forOne {
                        it.id!!.id shouldBe 1L
                        it.status shouldBe BestillingBatchStatus.Ferdig.value
                    }
                    forOne {
                        it.id!!.id shouldBe 2L
                        it.status shouldBe BestillingBatchStatus.Ferdig.value
                    }
                }
            }
        }

        test("ugyldigFoedselsEllerDnummer") {
            coEvery { skatteetatenClient.hentSkattekort(any(), any()) } returns
                aHentSkattekortResponse(
                    anArbeidstaker(
                        resultat = ResultatForSkattekort.UgyldigFoedselsEllerDnummer,
                        fnr = "01010100001",
                        inntektsaar = "2025",
                    ),
                ) andThen
                aHentSkattekortResponse(
                    anArbeidstaker(
                        resultat = ResultatForSkattekort.IkkeSkattekort,
                        fnr = "02020200002",
                        inntektsaar = "2025",
                    ),
                )

            databaseHas(
                aPerson(1L, "01010100001"),
                aBestillingsBatch(id = 1L, ref = "ref1", status = "NY"),
                aBestilling(personId = 1L, fnr = "01010100001", inntektsaar = 2025, batchId = 1L),
                aPerson(2L, "02020200002"),
                aBestillingsBatch(id = 2L, ref = "ref2", status = "NY"),
                aBestilling(personId = 2L, fnr = "02020200002", inntektsaar = 2025, batchId = 2L),
            )

            bestillingService.hentSkattekort()

            val updatedBatches: List<BestillingBatch> = tx(BestillingBatchRepository::list)
            val bestillingsAfter: List<Bestilling> = tx(BestillingRepository::getBestillingsKandidaterForBatch)
            val skattekortPerson1: List<Skattekort> =
                tx {
                    SkattekortRepository.findAllByPersonId(it, PersonId(1L), 2025, adminRole = false)
                }
            val skattekortPerson2: List<Skattekort> =
                tx {
                    SkattekortRepository.findAllByPersonId(it, PersonId(2L), 2025, adminRole = false)
                }
            val person1: Person = tx { PersonRepository.findPersonById(it, PersonId(1L)) }
            val person2: Person = tx { PersonRepository.findPersonById(it, PersonId(2L)) }

            assertSoftly {
                updatedBatches shouldNotBeNull {
                    forAll { it.status shouldBe BestillingBatchStatus.Ferdig.value }
                }

                bestillingsAfter shouldNotBeNull {
                    size shouldBe 0
                }

                skattekortPerson1 shouldNotBeNull {
                    size shouldBe 1
                    first() shouldNotBeNull {
                        identifikator shouldBe null
                        forskuddstrekkList shouldBe emptyList()
                        tilleggsopplysningList shouldBe emptyList()
                        resultatForSkattekort shouldBe ResultatForSkattekort.UgyldigFoedselsEllerDnummer
                    }
                }

                skattekortPerson2 shouldNotBeNull {
                    size shouldBe 1
                    first() shouldNotBeNull {
                        identifikator shouldBe null
                        forskuddstrekkList shouldBe emptyList()
                        tilleggsopplysningList shouldBe emptyList()
                        resultatForSkattekort shouldBe ResultatForSkattekort.IkkeSkattekort
                    }
                }

                person1 shouldNotBeNull {
                    flagget shouldBe true
                }
                person2 shouldNotBeNull {
                    flagget shouldBe false
                }
            }
        }

        test("UgyldigOrganisasjonsnummer") {
            coEvery { skatteetatenClient.hentSkattekort(any(), any()) } returns
                HentSkattekortResponse(
                    status = "FORESPOERSEL_OK",
                    arbeidsgiver =
                        listOf(
                            no.nav.sokos.skattekort.skatteetaten.hentskattekort.Arbeidsgiver(
                                arbeidsgiveridentifikator =
                                    no.nav.sokos.skattekort.skatteetaten.hentskattekort.Arbeidsgiveridentifikator(
                                        organisasjonsnummer = "666",
                                    ),
                                arbeidstaker =
                                    listOf(
                                        anArbeidstaker(
                                            resultat = ResultatForSkattekort.UgyldigOrganisasjonsnummer,
                                            fnr = "01010100001",
                                            inntektsaar = "2025",
                                        ),
                                    ),
                            ),
                        ),
                )

            databaseHas(
                aPerson(1L, "01010100001"),
                aBestillingsBatch(id = 1L, ref = "ref1", status = "NY"),
                aBestilling(personId = 1L, fnr = "01010100001", inntektsaar = 2025, batchId = 1L),
            )

            shouldThrow<UgyldigOrganisasjonsnummerException> {
                bestillingService.hentSkattekort()
            }

            val updatedBatches: List<BestillingBatch> = tx(BestillingBatchRepository::list)
            val bestillingsAfter: List<Bestilling> = tx(BestillingRepository::getBestillingsKandidaterForBatch)
            val skattekort: List<Skattekort> =
                tx {
                    SkattekortRepository.findAllByPersonId(it, PersonId(1L), 2025, adminRole = false)
                }
            val person1: Person = tx { PersonRepository.findPersonById(it, PersonId(1L)) }

            updatedBatches shouldNotBeNull {
                size shouldBe 1
                forOne { it.status shouldBe BestillingBatchStatus.Feilet.value }
            }

            bestillingsAfter shouldNotBeNull {
                size shouldBe 1
                forOne { it.bestillingsbatchId shouldBe null }
            }

            skattekort shouldBe emptyList()

            person1 shouldNotBeNull {
                flagget shouldBe false
            }
        }

        test("ikkeSkattekort med oppholdPaaSvalbard") {
            coEvery { skatteetatenClient.hentSkattekort(any(), any()) } returns
                aHentSkattekortResponse(
                    anArbeidstaker(
                        resultat = IkkeSkattekort,
                        fnr = "01010100001",
                        inntektsaar = "2025",
                        tilleggsopplysninger =
                            listOf(
                                Tilleggsopplysning.fromValue("oppholdPaaSvalbard"),
                            ),
                    ),
                )
            databaseHas(
                aPerson(personId = 1L, fnr = "01010100001"),
                aPerson(personId = 2L, fnr = "02020200002"),
                aPerson(personId = 3L, fnr = "03030300003"),
                aBestillingsBatch(id = 1L, ref = "ref1", status = "NY"),
                aBestillingsBatch(id = 2L, ref = "ref2", status = "NY"),
                aBestilling(personId = 1L, fnr = "01010100001", inntektsaar = 2025, batchId = 1L),
                aBestilling(personId = 2L, fnr = "02020200002", inntektsaar = 2025, batchId = 2L),
                aBestilling(personId = 3L, fnr = "03030300003", inntektsaar = 2025, batchId = 2L),
            )

            bestillingService.hentSkattekort()

            val updatedBatches: List<BestillingBatch> = tx(BestillingBatchRepository::list)
            val skattekort: List<Skattekort> =
                tx {
                    SkattekortRepository.findAllByPersonId(it, PersonId(1), 2025, adminRole = false)
                }
            val bestillingsAfter: List<Bestilling> = tx(BestillingRepository::getBestillingsKandidaterForBatch)

            assertSoftly {
                updatedBatches.count { it.status == BestillingBatchStatus.Ferdig.value } shouldBe 2

                bestillingsAfter shouldNotBeNull {
                    withClue("Vi bestilte 3 men fikk bare tilbake ett skattekort") {
                        size shouldBe 2
                    }
                }

                skattekort shouldNotBeNull {
                    size shouldBe 4
                    get(1) shouldNotBeNull {
                        resultatForSkattekort shouldBe IkkeSkattekort
                        identifikator shouldBe null
                        forskuddstrekkList shouldBe emptyList()
                        tilleggsopplysningList shouldContainExactly listOf(Tilleggsopplysning.fromValue("oppholdPaaSvalbard"))
                        kilde shouldBe SkattekortKilde.SKATTEETATEN.value
                    }
                    first() shouldNotBeNull {
                        resultatForSkattekort shouldBe IkkeSkattekort
                        kilde shouldBe SkattekortKilde.SYNTETISERT.value
                        generertFra shouldBe get(1).id
                        identifikator shouldBe null
                        withClue("Should generate forskuddstrekk for svalbard") {
                            forskuddstrekkList shouldContainExactly
                                listOf(
                                    aForskuddstrekk("Prosentkort", LOENN_FRA_NAV, 15.70),
                                    aForskuddstrekk("Prosentkort", UFOERETRYGD_FRA_NAV, 15.70),
                                    aForskuddstrekk("Prosentkort", PENSJON_FRA_NAV, 13.10),
                                )
                        }
                        tilleggsopplysningList shouldContainExactly listOf(Tilleggsopplysning.fromValue("oppholdPaaSvalbard"))
                    }
                }
            }
        }
        test("skattekortOpplysningerOk med oppholdPaaSvalbard") {
            coEvery { skatteetatenClient.hentSkattekort(any(), any()) } returns
                aHentSkattekortResponse(
                    anArbeidstaker(
                        resultat = SkattekortopplysningerOK,
                        fnr = "01010100001",
                        inntektsaar = "2025",
                        tilleggsopplysninger =
                            listOf(
                                Tilleggsopplysning.fromValue("oppholdPaaSvalbard"),
                            ),
                        skattekort =
                            aSkattekort(
                                utstedtDato = "2025-11-01",
                                identifikator = 10001,
                                forskuddstrekk =
                                    listOf(
                                        aSkdForskuddstrekk(LOENN_FRA_HOVEDARBEIDSGIVER, tabellNummer = "1337", trekkprosent = 43.21),
                                        aSkdForskuddstrekk(LOENN_FRA_NAV, 66.60),
                                        aSkdForskuddstrekk(PENSJON_FRA_NAV, 6.66),
                                        aSkdForskuddstrekk(UFOERETRYGD_FRA_NAV, 12.34),
                                    ),
                            ),
                    ),
                )
            databaseHas(
                aPerson(personId = 1L, fnr = "01010100001"),
                aBestillingsBatch(id = 1L, ref = "ref1", status = "NY"),
                aBestilling(personId = 1L, fnr = "01010100001", inntektsaar = 2025, batchId = 1L),
            )

            bestillingService.hentSkattekort()

            val updatedBatches: List<BestillingBatch> = tx(BestillingBatchRepository::list)
            val skattekort: List<Skattekort> =
                tx {
                    SkattekortRepository.findAllByPersonId(it, PersonId(1), 2025, adminRole = true)
                }
            val bestillingsAfter: List<Bestilling> = tx(BestillingRepository::getBestillingsKandidaterForBatch)

            assertSoftly {
                updatedBatches.count { it.status == BestillingBatchStatus.Ferdig.value } shouldBe 1

                bestillingsAfter shouldNotBeNull {
                    size shouldBe 0
                }

                skattekort shouldNotBeNull {
                    size shouldBe 2
                    withClue("The original Skattekort from Skattekort") {
                        last() shouldNotBeNull {
                            id shouldBe SkattekortId(1L)
                            generertFra shouldBe null
                            resultatForSkattekort shouldBe SkattekortopplysningerOK
                            kilde shouldBe SkattekortKilde.SKATTEETATEN.value
                            identifikator shouldBe "10001"
                            utstedtDato shouldBe kotlinx.datetime.LocalDate.parse("2025-11-01")
                            withClue("Should contain the received forskuddstrekk unchanged") {
                                forskuddstrekkList shouldContainAll
                                    listOf(
                                        aForskuddstrekk("Tabellkort", LOENN_FRA_HOVEDARBEIDSGIVER, tabellNummer = "1337", prosentSats = 43.21, antMndForTrekk = 12.0),
                                        aForskuddstrekk("Prosentkort", LOENN_FRA_NAV, 66.60),
                                        aForskuddstrekk("Prosentkort", PENSJON_FRA_NAV, 6.66),
                                        aForskuddstrekk("Prosentkort", UFOERETRYGD_FRA_NAV, 12.34),
                                    )
                            }
                            tilleggsopplysningList shouldContainExactly listOf(Tilleggsopplysning.fromValue("oppholdPaaSvalbard"))
                        }
                    }
                    withClue("A second Skattekort should be generated") {
                        forOne {
                            it.resultatForSkattekort shouldBe SkattekortopplysningerOK
                            it.kilde shouldBe SkattekortKilde.SYNTETISERT.value
                            it.generertFra shouldBe SkattekortId(1L)
                            it.identifikator shouldBe null
                            withClue("Should generate forskuddstrekk for svalbard") {
                                it.forskuddstrekkList shouldContainExactly
                                    listOf(
                                        aForskuddstrekk("Prosentkort", LOENN_FRA_NAV, 15.70),
                                        aForskuddstrekk("Prosentkort", UFOERETRYGD_FRA_NAV, 15.70),
                                        aForskuddstrekk("Prosentkort", PENSJON_FRA_NAV, 13.10),
                                    )
                            }
                            it.tilleggsopplysningList shouldContainExactly listOf(Tilleggsopplysning.fromValue("oppholdPaaSvalbard"))
                        }
                    }
                }
            }
        }

        test("ikkeTrekkplikt") {
            coEvery { skatteetatenClient.hentSkattekort(any(), any()) } returns
                aHentSkattekortResponse(
                    anArbeidstaker(
                        resultat = IkkeTrekkplikt,
                        fnr = "01010100001",
                        inntektsaar = "2025",
                    ),
                )
            databaseHas(
                aPerson(personId = 1L, fnr = "01010100001"),
                aBestillingsBatch(id = 1L, ref = "ref1", status = "NY"),
                aBestilling(personId = 1L, fnr = "01010100001", inntektsaar = 2025, batchId = 1L),
            )

            bestillingService.hentSkattekort()

            val updatedBatches: List<BestillingBatch> = tx(BestillingBatchRepository::list)
            val skattekort: List<Skattekort> =
                tx {
                    SkattekortRepository.findAllByPersonId(it, PersonId(1), 2025, adminRole = false)
                }
            val bestillingsAfter: List<Bestilling> = tx(BestillingRepository::getBestillingsKandidaterForBatch)

            assertSoftly {
                updatedBatches.count { it.status == BestillingBatchStatus.Ferdig.value } shouldBe 1

                bestillingsAfter shouldBe emptyList()

                skattekort shouldNotBeNull {
                    size shouldBe 2
                    last() shouldNotBeNull {
                        kilde shouldBe SkattekortKilde.SKATTEETATEN.value
                        resultatForSkattekort shouldBe IkkeTrekkplikt
                        utstedtDato shouldBe null
                        identifikator shouldBe null
                        forskuddstrekkList shouldBe emptyList()
                    }
                    first() shouldNotBeNull {
                        kilde shouldBe SkattekortKilde.SYNTETISERT.value
                        generertFra shouldBe last().id
                        resultatForSkattekort shouldBe IkkeTrekkplikt
                        utstedtDato shouldBe null
                        identifikator shouldBe null
                        withClue("Should generate frikort") {
                            forskuddstrekkList shouldContainExactly
                                listOf(
                                    aForskuddstrekk("Frikort", LOENN_FRA_NAV, frikortbeløp = null),
                                    aForskuddstrekk("Frikort", PENSJON_FRA_NAV, frikortbeløp = null),
                                    aForskuddstrekk("Frikort", UFOERETRYGD_FRA_NAV, frikortbeløp = null),
                                )
                        }
                    }
                }
            }
        }

        test("plukker ikke opp batch med status FEILET, gjør ingenting og trenger ikke mer data") {
            databaseHas(aBestillingsBatch(id = 1L, ref = "some-ref", status = "FEILET"))

            bestillingService.hentSkattekort()

            val updatedBatches = tx(BestillingBatchRepository::list)
            val auditAfter = tx { AuditRepository.getAuditByPersonId(it, PersonId(1L)) }

            assertSoftly {
                updatedBatches shouldNotBeNull {
                    size shouldBe 1
                    forExactly(1) { it.status shouldBe "FEILET" }
                }
                auditAfter shouldBe emptyList()
            }
        }

        test("plukker opp batch med status NY, får 404 fra skatt") {
            coEvery { skatteetatenClient.hentSkattekort(any(), any()) } throws RuntimeException("Feil ved henting av skattekort: 404")
            databaseHas(
                aPerson(fnr = "01010100001", personId = 1L),
                aPerson(fnr = "02020200002", personId = 2L),
                aPerson(fnr = "03030300003", personId = 3L),
                aBestillingsBatch(id = 1L, ref = "ref1", status = "NY"),
                aBestilling(personId = 1L, fnr = "01010100001", inntektsaar = 2025, batchId = 1L),
                aBestilling(personId = 2L, fnr = "02020200002", inntektsaar = 2025, batchId = 1L),
                aBestilling(personId = 3L, fnr = "03030300003", inntektsaar = 2025, batchId = 1L),
            )

            shouldThrow<RuntimeException> {
                bestillingService.hentSkattekort()
            }

            val updatedBatches = tx(BestillingBatchRepository::list)
            val auditPerson1: List<Audit> = tx { AuditRepository.getAuditByPersonId(it, PersonId(1L)) }
            val auditPerson2: List<Audit> = tx { AuditRepository.getAuditByPersonId(it, PersonId(2L)) }
            val auditPerson3: List<Audit> = tx { AuditRepository.getAuditByPersonId(it, PersonId(3L)) }
            val bestillingsAfter: List<Bestilling> = tx(BestillingRepository::getBestillingsKandidaterForBatch)

            assertSoftly {
                withClue("Should mark batch as FEILET") {
                    updatedBatches shouldNotBeNull {
                        first().status shouldBe BestillingBatchStatus.Feilet.value
                    }
                }

                withClue("Should not delete bestilling or remove batch association") {
                    bestillingsAfter shouldNotBeNull {
                        size shouldBe 3
                        forAll {
                            it.bestillingsbatchId!!.id shouldBe 1L
                        }
                    }
                }

                withClue("Should create auditlog for all persons in batch") {
                    auditPerson1 + auditPerson2 + auditPerson3 shouldNotBeNull {
                        forAll {
                            it.tag shouldBe AuditTag.HENTING_AV_SKATTEKORT_FEILET
                        }
                    }
                }
            }
        }

        test("plukker ikke opp batch med status FEILET men tar den andre istedenfor") {
            coEvery { skatteetatenClient.hentSkattekort(any(), any()) } returns aHentSkattekortResponse(anArbeidstaker(resultat = IkkeSkattekort, fnr = "02020200002", inntektsaar = "2025"))

            databaseHas(
                aPerson(fnr = "01010100001", personId = 1L),
                aPerson(fnr = "02020200002", personId = 2L),
                aBestillingsBatch(id = 1L, ref = "ref1", status = "FEILET"),
                aBestillingsBatch(id = 2L, ref = "ref2", status = "NY"),
                aBestilling(personId = 1L, fnr = "01010100001", inntektsaar = 2025, batchId = 1L),
                aBestilling(personId = 2L, fnr = "02020200002", inntektsaar = 2025, batchId = 2L),
            )

            bestillingService.hentSkattekort()

            val updatedBatches: List<BestillingBatch> = tx(BestillingBatchRepository::list)
            val bestillingsAfter: List<Bestilling> = tx(BestillingRepository::getBestillingsKandidaterForBatch)
            val person1: Person = tx { PersonRepository.findPersonById(it, PersonId(1L)) }

            assertSoftly {
                bestillingsAfter shouldNotBeNull {
                    size shouldBe 1
                    first().bestillingsbatchId!!.id shouldBe 1L
                }

                updatedBatches shouldNotBeNull {
                    first().status shouldBe BestillingBatchStatus.Feilet.value
                    last().status shouldBe BestillingBatchStatus.Ferdig.value
                }
                person1 shouldNotBeNull {
                    flagget shouldBe false
                }
            }
        }

        test("henter skattekort og opprett utsending til OS_STOR") {
            val fnr = "01010100001"
            coEvery { skatteetatenClient.hentSkattekort(any(), any()) } returns
                aHentSkattekortResponse(
                    aSkattekortFor(fnr, 10001),
                )

            databaseHas(
                aPerson(1L, fnr),
                anAbonnement(1L, personId = 1L, inntektsaar = 2025, isBulkRequest = true),
                aBestillingsBatch(1, "ref1", BestillingBatchStatus.Ny.value),
                aBestilling(1L, fnr, 2025, 1L),
            )

            bestillingService.hentSkattekort()

            val updatedBatches: List<BestillingBatch> = tx(BestillingBatchRepository::list)
            val skattekort: List<Skattekort> = tx { SkattekortRepository.findAllByPersonId(it, PersonId(1), 2025, adminRole = false) }
            val bestillingsAfter: List<Bestilling> = tx(BestillingRepository::getBestillingsKandidaterForBatch)
            val utsendingerAfter: List<Utsending> = tx(UtsendingRepository::getAllUtsendinger)

            assertSoftly {
                updatedBatches shouldNotBeNull {
                    size shouldBe 1
                    first() shouldNotBeNull {
                        status shouldBe BestillingBatchStatus.Ferdig.value
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

                bestillingsAfter shouldNotBeNull {
                    size shouldBe 0
                }

                assertSoftly {
                    utsendingerAfter shouldNotBeNull {
                        size shouldBe 1
                        shouldContainAllIgnoringFields(
                            listOf(
                                Utsending(UtsendingId(1), Personidentifikator(fnr), 2025, Forsystem.OPPDRAGSSYSTEMET_STOR),
                            ),
                            Utsending::opprettet,
                        )
                    }
                }
            }
        }
    })

fun aSkattekortFor(
    fnr: String,
    id: Long,
) = anArbeidstaker(
    resultat = SkattekortopplysningerOK,
    fnr = fnr,
    inntektsaar = "2025",
    skattekort =
        no.nav.sokos.skattekort.skatteetaten.hentskattekort.Skattekort(
            utstedtDato = "2025-11-01",
            skattekortidentifikator = id,
            forskuddstrekk =
                listOf(
                    Forskuddstrekk(
                        trekkode = LOENN_FRA_NAV.value,
                        trekkprosent = Trekkprosent(valueOf(25)),
                    ),
                    Forskuddstrekk(
                        trekkode = UFOERETRYGD_FRA_NAV.value,
                        trekkprosent = Trekkprosent(valueOf(28)),
                    ),
                ),
        ),
)
