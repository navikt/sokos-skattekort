package no.nav.sokos.skattekort.module.skattekort

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.FunSpec
import io.kotest.inspectors.forAll
import io.kotest.inspectors.forExactly
import io.kotest.inspectors.forOne
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.coEvery
import io.mockk.mockk
import kotliquery.queryOf

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
import no.nav.sokos.skattekort.module.utsending.Utsending
import no.nav.sokos.skattekort.module.utsending.UtsendingRepository
import no.nav.sokos.skattekort.skatteetaten.SkatteetatenClient
import no.nav.sokos.skattekort.skatteetaten.bestillskattekort.BestillSkattekortResponse
import no.nav.sokos.skattekort.util.SQLUtils.transaction

class BestillingsServiceTest :
    FunSpec({
        extensions(DbListener)

        val skatteetatenClient = mockk<SkatteetatenClient>()

        val bestillingService: BestillingService by lazy {
            BestillingService(DbListener.dataSource, skatteetatenClient, PersonService(DbListener.dataSource))
        }

        test("vi kan opprette bestillingsbatch og knytte bestillinger til batch") {
            val bestillingsreferanse = "some-bestillings-ref"

            coEvery { skatteetatenClient.bestillSkattekort(any()) } returns
                BestillSkattekortResponse(
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

            bestillingService.opprettBestillingsbatch()

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

        test("henter skattekort for batch to ganger") {

            coEvery { skatteetatenClient.hentSkattekort(any()) } returns
                aHentSkattekortResponseFromFile("skatteetaten/hentSkattekort/skattekortopplysningerOK.json") andThen
                aHentSkattekortResponseFromFile("skatteetaten/hentSkattekort/2skattekortopplysningerOK.json")

            // Sett inn bestillinger uten bestillingsbatch.
            DbListener.loadDataSet("database/person/persondata.sql")
            DbListener.loadDataSet("database/bestillinger/bestillinger.sql")

            databaseHas(
                anAbonnement(1L, personId = 1L, inntektsaar = 2025),
                anAbonnement(2L, personId = 2L, inntektsaar = 2025),
                anAbonnement(3L, personId = 3L, inntektsaar = 2025),
            )

            bestillingService.hentSkattekort()

            val updatedBatches: List<BestillingBatch> = tx(BestillingBatchRepository::list)
            val skattekort: List<Skattekort> = tx { SkattekortRepository.findAllByPersonId(it, PersonId(1), 2025) }
            val bestillingsAfter: List<Bestilling> = tx(BestillingRepository::getAllBestilling)
            val utsendingerAfter: List<Utsending> = tx(UtsendingRepository::getAllUtsendinger)

            assertSoftly("After the first run") {
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

                bestillingsAfter shouldNotBeNull {
                    size shouldBe 2
                    forAll { it.bestillingsbatchId!!.id shouldBe 2L }
                }

                skattekort shouldNotBeNull {
                    size shouldBe 1
                    forOne {
                        it.identifikator shouldBe "54407"
                        it.resultatForSkattekort shouldBe SkattekortopplysningerOK
                        it.forskuddstrekkList shouldContainExactlyInAnyOrder
                            listOf(
                                aForskuddstrekk(Tabellkort::class.simpleName!!, Trekkode.LOENN_FRA_HOVEDARBEIDSGIVER, 43.00, 10.5, "8140"),
                                aForskuddstrekk(Prosentkort::class.simpleName!!, Trekkode.LOENN_FRA_BIARBEIDSGIVER, 43.00),
                                aForskuddstrekk(Prosentkort::class.simpleName!!, Trekkode.LOENN_FRA_NAV, 43.00),
                                aForskuddstrekk(Prosentkort::class.simpleName!!, Trekkode.UFOERETRYGD_FRA_NAV, 43.00),
                                aForskuddstrekk(Prosentkort::class.simpleName!!, Trekkode.UFOEREYTELSER_FRA_ANDRE, 43.00),
                            )
                        it.tilleggsopplysningList shouldContainExactlyInAnyOrder
                            listOf(
                                "oppholdPaaSvalbard",
                                "kildeskattPaaPensjon",
                                "oppholdITiltakssone",
                                "kildeskattPaaLoenn",
                            ).map { Tilleggsopplysning(it) }
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

            assertSoftly("And after the second run") {
                updatedBatchesSecondRun.count { it.status == BestillingBatchStatus.Ferdig.value } shouldBe 2
                bestillingsAfterSecondRun.size shouldBe 0
                skattekortAfterSecondRun.size shouldBe 3
                utsendingerAfterSecondRun.size shouldBe 3
            }
        }

        test("ugyldigFoedselsEllerDnummer") {
            coEvery { skatteetatenClient.hentSkattekort(any()) } returns
                aHentSkattekortResponse(
                    resultat = ResultatForSkattekort.UgyldigFoedselsEllerDnummer,
                    fnr = "01010100001",
                    inntektsaar = "2025",
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
                    resultat = IkkeSkattekort,
                    fnr = "01010100001",
                    inntektsaar = "2025",
                    tilleggsopplysninger =
                        listOf(
                            Tilleggsopplysning("oppholdPaaSvalbard"),
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
                                aForskuddstrekk("Prosentkort", Trekkode.LOENN_FRA_NAV, 15.70),
                                aForskuddstrekk("Prosentkort", Trekkode.UFOERETRYGD_FRA_NAV, 15.70),
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
            coEvery { skatteetatenClient.hentSkattekort(any()) } throws
                RuntimeException("Feil ved henting av skattekort: 404")
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
            coEvery { skatteetatenClient.hentSkattekort(any()) } returns
                aHentSkattekortResponse(resultat = IkkeSkattekort, fnr = "02020200002", inntektsaar = "2025")

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
            }
        }
    })
