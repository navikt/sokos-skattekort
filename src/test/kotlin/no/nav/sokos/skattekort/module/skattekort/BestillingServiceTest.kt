package no.nav.sokos.skattekort.module.skattekort

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.inspectors.forAll
import io.kotest.inspectors.forExactly
import io.kotest.inspectors.forOne
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.coEvery
import io.mockk.mockk

import no.nav.sokos.skattekort.listener.DbListener
import no.nav.sokos.skattekort.module.person.Audit
import no.nav.sokos.skattekort.module.person.AuditRepository
import no.nav.sokos.skattekort.module.person.AuditTag
import no.nav.sokos.skattekort.module.person.Person
import no.nav.sokos.skattekort.module.person.PersonId
import no.nav.sokos.skattekort.module.person.PersonRepository
import no.nav.sokos.skattekort.module.person.PersonService
import no.nav.sokos.skattekort.module.skattekort.ResultatForSkattekort.IkkeSkattekort
import no.nav.sokos.skattekort.module.skattekort.ResultatForSkattekort.SkattekortopplysningerOK
import no.nav.sokos.skattekort.module.skattekort.Trekkode.LOENN_FRA_BIARBEIDSGIVER
import no.nav.sokos.skattekort.module.skattekort.Trekkode.LOENN_FRA_HOVEDARBEIDSGIVER
import no.nav.sokos.skattekort.module.skattekort.Trekkode.LOENN_FRA_NAV
import no.nav.sokos.skattekort.module.skattekort.Trekkode.UFOERETRYGD_FRA_NAV
import no.nav.sokos.skattekort.module.skattekort.Trekkode.UFOEREYTELSER_FRA_ANDRE
import no.nav.sokos.skattekort.module.utsending.Utsending
import no.nav.sokos.skattekort.module.utsending.UtsendingRepository
import no.nav.sokos.skattekort.skatteetaten.SkatteetatenClient

val aListOfForskuddstrekk =
    listOf(
        aTabellkort(trekkode = LOENN_FRA_HOVEDARBEIDSGIVER, prosentSats = 43, antMndForTrekk = 10.5, tabellNummer = "8140"),
        aProsentkort(trekkode = LOENN_FRA_BIARBEIDSGIVER, prosentSats = 43),
        aProsentkort(trekkode = LOENN_FRA_NAV, prosentSats = 43),
        aProsentkort(trekkode = UFOERETRYGD_FRA_NAV, prosentSats = 43),
        aProsentkort(trekkode = UFOEREYTELSER_FRA_ANDRE, prosentSats = 43),
    )

val alleTilleggsopplysningene =
    listOf(
        Tilleggsopplysning("oppholdPaaSvalbard"),
        Tilleggsopplysning("kildeskattPaaPensjon"),
        Tilleggsopplysning("oppholdITiltakssone"),
        Tilleggsopplysning("kildeskattPaaLoenn"),
    )

fun aSkattekortFor(
    fnr: String,
    id: Long,
) = anArbeidstaker(
    resultat = SkattekortopplysningerOK,
    fnr = fnr,
    inntektsaar = "2025",
    skattekort =
        aSkattekort(
            utstedtDato = "2025-11-01",
            identifikator = id,
            forskuddstrekk = aListOfForskuddstrekk,
        ),
)

class BestillingServiceTest :
    FunSpec({
        extensions(DbListener)

        val skatteetatenClient = mockk<SkatteetatenClient>()

        val bestillingService: BestillingService by lazy {
            BestillingService(DbListener.dataSource, skatteetatenClient, PersonService(DbListener.dataSource))
        }

        test("vi kan opprette bestillingsbatch og knytte bestillinger til batch") {

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
                aBestilling(1L, "01010100001", 2025, null),
                aBestilling(2L, "02020200002", 2025, null),
                aBestilling(3L, "03030300003", 2025, null),
            )

            bestillingService.opprettBestillingsbatch()

            val bestillings: List<Bestilling> = tx(BestillingRepository::getAllBestilling)
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

        test("henter skattekort enkleste scenario") {
            coEvery { skatteetatenClient.hentSkattekort(any()) } returns
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
            val skattekort: List<Skattekort> = tx { SkattekortRepository.findAllByPersonId(it, PersonId(1), 2025) }
            val bestillingsAfter: List<Bestilling> = tx(BestillingRepository::getAllBestilling)
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
                            size shouldBe 5
                        }
                    }
                }

                bestillingsAfter shouldNotBeNull {
                    size shouldBe 0
                }

                utsendingerAfter.size shouldBe 1
            }
        }

        test("henter skattekort med tilleggsopplysning") {
            coEvery { skatteetatenClient.hentSkattekort(any()) } returns
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
                                        aTabellkort(trekkode = LOENN_FRA_HOVEDARBEIDSGIVER, prosentSats = 43, antMndForTrekk = 10.5, tabellNummer = "8140"),
                                        aProsentkort(trekkode = LOENN_FRA_BIARBEIDSGIVER, prosentSats = 43),
                                        aProsentkort(trekkode = LOENN_FRA_NAV, prosentSats = 43),
                                        aProsentkort(trekkode = UFOERETRYGD_FRA_NAV, prosentSats = 43),
                                        aProsentkort(trekkode = UFOEREYTELSER_FRA_ANDRE, prosentSats = 43),
                                    ),
                            ),
                        tilleggsopplysninger = alleTilleggsopplysningene,
                    ),
                )

            databaseHas(
                aPerson(1L, "01010100001"),
                anAbonnement(1L, personId = 1L, inntektsaar = 2025),
                aBestillingsBatch(1, "ref1", BestillingBatchStatus.Ny.value),
                aBestilling(1L, "01010100001", 2025, 1L),
            )

            bestillingService.hentSkattekort()

            val skattekort: List<Skattekort> = tx { SkattekortRepository.findAllByPersonId(it, PersonId(1), 2025) }
            val bestillingsAfter: List<Bestilling> = tx(BestillingRepository::getAllBestilling)
            val utsendingerAfter: List<Utsending> = tx(UtsendingRepository::getAllUtsendinger)

            assertSoftly {

                skattekort shouldNotBeNull {
                    size shouldBe 1
                    first() shouldNotBeNull {
                        identifikator shouldBe "10001"
                        resultatForSkattekort shouldBe SkattekortopplysningerOK
                        forskuddstrekkList shouldNotBeNull {
                            size shouldBe 5
                            listOf(
                                aForskuddstrekk("Tabellkort", LOENN_FRA_HOVEDARBEIDSGIVER, tabellNummer = "8140", prosentSats = 43.0, antMndForTrekk = 10.5),
                                aForskuddstrekk("Prosentkort", LOENN_FRA_BIARBEIDSGIVER, 43.0),
                                aForskuddstrekk("Prosentkort", LOENN_FRA_NAV, 43.0),
                                aForskuddstrekk("Prosentkort", UFOERETRYGD_FRA_NAV, 43.0),
                                aForskuddstrekk("Prosentkort", UFOEREYTELSER_FRA_ANDRE, 43.0),
                            )
                        }
                        tilleggsopplysningList shouldNotBeNull {
                            size shouldBe 4
                            shouldContainExactly(
                                Tilleggsopplysning("oppholdPaaSvalbard"),
                                Tilleggsopplysning("kildeskattPaaPensjon"),
                                Tilleggsopplysning("oppholdITiltakssone"),
                                Tilleggsopplysning("kildeskattPaaLoenn"),
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

        test("henter skattekort for batch to ganger") {

            coEvery { skatteetatenClient.hentSkattekort(any()) } returns
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

            val updatedBatches: List<BestillingBatch> = tx(BestillingBatchRepository::list)
            val skattekort: List<Skattekort> = tx { SkattekortRepository.findAllByPersonId(it, PersonId(1), 2025) }
            val bestillingsAfter: List<Bestilling> = tx(BestillingRepository::getAllBestilling)
            val utsendingerAfter: List<Utsending> = tx(UtsendingRepository::getAllUtsendinger)

            assertSoftly("Etter første kjøring skal en batch få status Ferdig") {
                updatedBatches shouldNotBeNull {
                    size shouldBe 2
                    forOne {
                        it.id!!.id shouldBe 1L
                        it.status shouldBe BestillingBatchStatus.Ferdig.value
                    }
                    forOne {
                        it.id!!.id shouldBe 2L
                        it.status shouldBe BestillingBatchStatus.Ny.value
                    }
                }

                withClue("1 av 4 bestillinger skal være slettet") {
                    bestillingsAfter shouldNotBeNull {
                        size shouldBe 3
                        forExactly(2) { it.bestillingsbatchId!!.id shouldBe 2L }
                        withClue("1 bestillinger ikke tilknyttet batch") {
                            forOne {
                                it.id!!.id shouldBe 4L
                                it.bestillingsbatchId shouldBe null
                            }
                        }
                    }
                }

                skattekort shouldNotBeNull {
                    size shouldBe 1
                    withClue("Et skattekort skal være opprettet") {
                        forOne {
                            it.identifikator shouldBe "10001"
                            it.resultatForSkattekort shouldBe SkattekortopplysningerOK
                            it.forskuddstrekkList shouldNotBeNull {
                                size shouldBe 5
                            }
                        }
                    }
                }

                utsendingerAfter.size shouldBe 1
            }

            bestillingService.hentSkattekort()

            val updatedBatchesSecondRun: List<BestillingBatch> = tx(BestillingBatchRepository::list)
            val bestillingsAfterSecondRun: List<Bestilling> = tx(BestillingRepository::getAllBestilling)
            val skattekortAfterSecondRun: List<Skattekort> =
                tx {
                    listOf(
                        SkattekortRepository.findAllByPersonId(it, PersonId(1), 2025),
                        SkattekortRepository.findAllByPersonId(it, PersonId(2), 2025),
                        SkattekortRepository.findAllByPersonId(it, PersonId(3), 2025),
                    ).flatMap { it }
                }
            val utsendingerAfterSecondRun: List<Utsending> = tx(UtsendingRepository::getAllUtsendinger)

            assertSoftly("Og etter andre kjøring") {
                updatedBatchesSecondRun.count { it.status == BestillingBatchStatus.Ferdig.value } shouldBe 2
                bestillingsAfterSecondRun.size shouldBe 1
                skattekortAfterSecondRun.size shouldBe 3
                utsendingerAfterSecondRun.size shouldBe 3
            }
        }

        test("ugyldigFoedselsEllerDnummer") {
            coEvery { skatteetatenClient.hentSkattekort(any()) } returns
                aHentSkattekortResponse(
                    anArbeidstaker(
                        resultat = ResultatForSkattekort.UgyldigFoedselsEllerDnummer,
                        fnr = "01010100001",
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
            val bestillingsAfter: List<Bestilling> = tx(BestillingRepository::getAllBestilling)
            val skattekort: List<Skattekort> =
                tx {
                    SkattekortRepository.findAllByPersonId(it, PersonId(1L), 2025)
                }
            val person1: Person = tx { PersonRepository.findPersonById(it, PersonId(1L)) }
            val person2: Person = tx { PersonRepository.findPersonById(it, PersonId(2L)) }

            assertSoftly {
                updatedBatches shouldNotBeNull {
                    forOne { it.status shouldBe BestillingBatchStatus.Ny.value }
                    forOne { it.status shouldBe BestillingBatchStatus.Ferdig.value }
                }

                bestillingsAfter shouldNotBeNull {
                    size shouldBe 1
                    forExactly(0) { it.bestillingsbatchId!!.id shouldBe 1L }
                    forOne { it.bestillingsbatchId!!.id shouldBe 2L }
                }

                skattekort shouldNotBeNull {
                    size shouldBe 1
                    first() shouldNotBeNull {
                        identifikator shouldBe null
                        forskuddstrekkList shouldBe emptyList()
                        tilleggsopplysningList shouldBe emptyList()
                        resultatForSkattekort shouldBe ResultatForSkattekort.UgyldigFoedselsEllerDnummer
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

        test("ikkeSkattekort med oppholdPaaSvalbard") {
            coEvery { skatteetatenClient.hentSkattekort(any()) } returns
                aHentSkattekortResponse(
                    anArbeidstaker(
                        resultat = IkkeSkattekort,
                        fnr = "01010100001",
                        inntektsaar = "2025",
                        tilleggsopplysninger =
                            listOf(
                                Tilleggsopplysning("oppholdPaaSvalbard"),
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
                    SkattekortRepository.findAllByPersonId(it, PersonId(1), 2025)
                }
            val bestillingsAfter: List<Bestilling> = tx(BestillingRepository::getAllBestilling)

            assertSoftly {
                updatedBatches.count { it.status == BestillingBatchStatus.Ny.value } shouldBe 1
                updatedBatches.count { it.status == BestillingBatchStatus.Ferdig.value } shouldBe 1

                bestillingsAfter shouldNotBeNull {
                    size shouldBe 2
                    forExactly(0) { it.bestillingsbatchId!!.id shouldBe 1L }
                    forExactly(2) { it.bestillingsbatchId!!.id shouldBe 2 }
                }

                skattekort shouldNotBeNull {
                    size shouldBe 1
                    first() shouldNotBeNull {
                        resultatForSkattekort shouldBe IkkeSkattekort
                        identifikator shouldBe null
                        forskuddstrekkList shouldContainExactly
                            listOf(
                                aForskuddstrekk("Prosentkort", LOENN_FRA_NAV, 15.70),
                                aForskuddstrekk("Prosentkort", UFOERETRYGD_FRA_NAV, 15.70),
                                aForskuddstrekk("Prosentkort", Trekkode.PENSJON_FRA_NAV, 13.00),
                            )
                        tilleggsopplysningList shouldContainExactly listOf(Tilleggsopplysning("oppholdPaaSvalbard"))
                        kilde shouldBe SkattekortKilde.SYNTETISERT.value
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
            coEvery { skatteetatenClient.hentSkattekort(any()) } throws RuntimeException("Feil ved henting av skattekort: 404")
            databaseHas(
                aPerson(fnr = "01010100001", personId = 1L),
                aBestillingsBatch(id = 1L, ref = "ref1", status = "NY"),
                aBestilling(personId = 1L, fnr = "01010100001", inntektsaar = 2025, batchId = 1L),
            )

            bestillingService.hentSkattekort()

            val updatedBatches = tx(BestillingBatchRepository::list)
            val auditAfter: List<Audit> = tx { AuditRepository.getAuditByPersonId(it, PersonId(1L)) }

            assertSoftly {
                updatedBatches shouldNotBeNull {
                    first().status shouldBe BestillingBatchStatus.Feilet.value
                }

                auditAfter shouldNotBeNull {
                    size shouldBe 1
                    first().tag shouldBe AuditTag.HENTING_AV_SKATTEKORT_FEILET
                }
            }
        }

        test("plukker ikke opp batch med status FEILET men tar den andre istedenfor") {
            coEvery { skatteetatenClient.hentSkattekort(any()) } returns aHentSkattekortResponse(anArbeidstaker(resultat = IkkeSkattekort, fnr = "02020200002", inntektsaar = "2025"))

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
            val bestillingsAfter: List<Bestilling> = tx(BestillingRepository::getAllBestilling)
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
    })
