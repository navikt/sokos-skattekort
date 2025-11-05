package no.nav.sokos.skattekort.module.skattekort

import java.nio.file.Files
import java.nio.file.Paths

import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.FunSpec
import io.kotest.inspectors.forExactly
import io.kotest.inspectors.forOne
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.coEvery
import io.mockk.mockk
import kotliquery.queryOf

import no.nav.sokos.skattekort.listener.DbListener
import no.nav.sokos.skattekort.module.person.Foedselsnummer
import no.nav.sokos.skattekort.module.person.FoedselsnummerId
import no.nav.sokos.skattekort.module.person.Person
import no.nav.sokos.skattekort.module.person.PersonId
import no.nav.sokos.skattekort.module.person.PersonService
import no.nav.sokos.skattekort.module.person.Personidentifikator
import no.nav.sokos.skattekort.module.utsending.Utsending
import no.nav.sokos.skattekort.module.utsending.UtsendingRepository
import no.nav.sokos.skattekort.skatteetaten.SkatteetatenClient
import no.nav.sokos.skattekort.skatteetaten.bestillskattekort.BestillSkattekortResponse
import no.nav.sokos.skattekort.skatteetaten.hentskattekort.HentSkattekortResponse
import no.nav.sokos.skattekort.util.SQLUtils.transaction

class BestillingsServiceTest :
    FunSpec({
        extensions(DbListener)

        val skatteetatenClient = mockk<SkatteetatenClient>()
        val personService = mockk<PersonService>()

        coEvery { personService.findPersonByFnr(any(), any()) } returns
            Person(
                id = PersonId(1),
                false,
                Foedselsnummer(
                    FoedselsnummerId(1),
                    PersonId(1),
                    LocalDate(2000, 1, 1),
                    Personidentifikator("12345678901"),
                ),
            )

        val bestillingService: BestillingService by lazy {
            BestillingService(DbListener.dataSource, skatteetatenClient, personService)
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

        test("henter skattekort for batch") {

            coEvery { skatteetatenClient.hentSkattekort(any()) } returns
                hentSkattekortResponseFromFile("src/test/resources/skatteetaten/skattekortopplysningerOK.json") andThen
                hentSkattekortResponseFromFile("src/test/resources/skatteetaten/2skattekortopplysningerOK.json")

            // Sett inn bestillinger uten bestillingsbatch.
            DbListener.loadDataSet("database/person/persondata.sql")
            DbListener.loadDataSet("database/bestillinger/bestillinger.sql")
            DbListener.loadDataSet("database/bestillinger/abonnementer.sql")

            val bestillingsBefore: List<Bestilling> =
                DbListener.dataSource.transaction { session ->
                    BestillingRepository.getAllBestilling(session)
                }

            bestillingService.hentSkattekort()

            val updatedBatches: List<BestillingBatch> =
                DbListener.dataSource.transaction { session ->
                    BestillingBatchRepository.list(session)
                }

            val skattekort: List<Skattekort> =
                DbListener.dataSource.transaction { session ->
                    SkattekortRepository.findAllByPersonId(session, PersonId(1), 2025)
                }

            val bestillingsAfter: List<Bestilling> =
                DbListener.dataSource.transaction { session ->
                    BestillingRepository.getAllBestilling(session)
                }
            val utsendingerAfter: List<Utsending> =
                DbListener.dataSource.transaction { tx ->
                    UtsendingRepository.getAllUtsendinger(tx)
                }

            assertSoftly {

                bestillingsBefore.size shouldBe 3
                bestillingsBefore.forExactly(1) { it.bestillingsbatchId!!.id shouldBe 1L }
                bestillingsBefore.forExactly(2) { it.bestillingsbatchId!!.id shouldBe 2L }

                updatedBatches.count { it.status == BestillingBatchStatus.Ny.value } shouldBe 1
                updatedBatches.count { it.status == BestillingBatchStatus.Ferdig.value } shouldBe 1

                bestillingsAfter.size shouldBe 2
                bestillingsAfter.forExactly(0) { it.bestillingsbatchId!!.id shouldBe 1L }
                bestillingsAfter.forExactly(2) { it.bestillingsbatchId!!.id shouldBe 2L }

                skattekort.size shouldBe 1
                val skattekortet = skattekort.first()
                skattekortet.identifikator shouldBe "54407"
                skattekortet.forskuddstrekkList shouldContainExactlyInAnyOrder
                    listOf(
                        aForskuddstrekk(Tabellkort::class.simpleName!!, Trekkode.LOENN_FRA_HOVEDARBEIDSGIVER, 43.00, 10.5, "8140"),
                        aForskuddstrekk(Prosentkort::class.simpleName!!, Trekkode.LOENN_FRA_BIARBEIDSGIVER, 43.00),
                        aForskuddstrekk(Prosentkort::class.simpleName!!, Trekkode.LOENN_FRA_NAV, 43.00),
                        aForskuddstrekk(Prosentkort::class.simpleName!!, Trekkode.UFOERETRYGD_FRA_NAV, 43.00),
                        aForskuddstrekk(Prosentkort::class.simpleName!!, Trekkode.UFOEREYTELSER_FRA_ANDRE, 43.00),
                    )
                skattekortet.tilleggsopplysningList.size shouldBe 4
                skattekortet.tilleggsopplysningList shouldContainExactlyInAnyOrder
                    listOf(
                        "oppholdPaaSvalbard",
                        "kildeskattPaaPensjon",
                        "oppholdITiltakssone",
                        "kildeskattPaaLoenn",
                    ).map { Tilleggsopplysning(it) }
                skattekortet.resultatForSkattekort shouldBe ResultatForSkattekort.SkattekortopplysningerOK
                utsendingerAfter.size shouldBe 1
            }

            bestillingService.hentSkattekort()

            val updatedBatchesSecondRun: List<BestillingBatch> =
                DbListener.dataSource.transaction { session ->
                    BestillingBatchRepository.list(session)
                }
            val bestillingsAfterSecondRun: List<Bestilling> =
                DbListener.dataSource.transaction { session ->
                    BestillingRepository.getAllBestilling(session)
                }

            val skattekortAfterSecondRun: List<Skattekort> =
                DbListener.dataSource.transaction { session ->
                    listOf(
                        SkattekortRepository.findAllByPersonId(session, PersonId(1), 2025),
                        SkattekortRepository.findAllByPersonId(session, PersonId(2), 2025),
                        SkattekortRepository.findAllByPersonId(session, PersonId(3), 2025),
                    ).flatMap { it }
                }

            val utsendingerAfterSecondRun: List<Utsending> =
                DbListener.dataSource.transaction { tx ->
                    UtsendingRepository.getAllUtsendinger(tx)
                }

            assertSoftly {
                updatedBatchesSecondRun.count { it.status == BestillingBatchStatus.Ferdig.value } shouldBe 2
                bestillingsAfterSecondRun.size shouldBe 0
                skattekortAfterSecondRun.size shouldBe 3
                utsendingerAfterSecondRun.size shouldBe 3
            }
        }

        test("ugyldigFoedselsEllerDnummer") {
            coEvery { skatteetatenClient.hentSkattekort(any()) } returns hentSkattekortResponseFromFile("src/test/resources/skatteetaten/ugyldigFoedselsEllerDnummer.json")
            coEvery { personService.flaggPerson(any(), any()) } returns true
            DbListener.loadDataSet("database/person/persondata.sql")
            DbListener.loadDataSet("database/bestillinger/bestillinger.sql")

            val bestillingsBefore: List<Bestilling> =
                DbListener.dataSource.transaction { session ->
                    BestillingRepository.getAllBestilling(session)
                }

            bestillingService.hentSkattekort()

            val updatedBatches: List<BestillingBatch> =
                DbListener.dataSource.transaction { session ->
                    BestillingBatchRepository.list(session)
                }

            val skattekort: List<Skattekort> =
                DbListener.dataSource.transaction { session ->
                    SkattekortRepository.findAllByPersonId(session, PersonId(1), 2025)
                }

            val bestillingsAfter: List<Bestilling> =
                DbListener.dataSource.transaction { session ->
                    BestillingRepository.getAllBestilling(session)
                }

            assertSoftly {
                bestillingsBefore.size shouldBe 3
                bestillingsBefore.forExactly(1) { it.bestillingsbatchId!!.id shouldBe 1L }
                bestillingsBefore.forExactly(2) { it.bestillingsbatchId!!.id shouldBe 2L }

                updatedBatches.count { it.status == BestillingBatchStatus.Ny.value } shouldBe 1
                updatedBatches.count { it.status == BestillingBatchStatus.Ferdig.value } shouldBe 1
                bestillingsAfter.size shouldBe 2
                bestillingsAfter.forExactly(0) { it.bestillingsbatchId!!.id shouldBe 1L }
                bestillingsAfter.forExactly(2) { it.bestillingsbatchId!!.id shouldBe 2L }

                val skattekortet = skattekort.first()
                skattekortet.identifikator shouldBe null
                skattekortet.forskuddstrekkList shouldBe emptyList()
                skattekortet.tilleggsopplysningList shouldBe emptyList()
                skattekortet.resultatForSkattekort shouldBe ResultatForSkattekort.UgyldigFoedselsEllerDnummer
            }
        }

        test("ikkeSkattekort med oppholdPaaSvalbard") {
            coEvery { skatteetatenClient.hentSkattekort(any()) } returns hentSkattekortResponseFromFile("src/test/resources/skatteetaten/ikkeSkattekort.json")
            DbListener.loadDataSet("database/person/persondata.sql")
            DbListener.loadDataSet("database/bestillinger/bestillinger.sql")

            val bestillingsBefore: List<Bestilling> =
                DbListener.dataSource.transaction { session ->
                    BestillingRepository.getAllBestilling(session)
                }

            bestillingService.hentSkattekort()

            val updatedBatches: List<BestillingBatch> =
                DbListener.dataSource.transaction { session ->
                    BestillingBatchRepository.list(session)
                }

            val skattekort: List<Skattekort> =
                DbListener.dataSource.transaction { session ->
                    SkattekortRepository.findAllByPersonId(session, PersonId(1), 2025)
                }

            val bestillingsAfter: List<Bestilling> =
                DbListener.dataSource.transaction { session ->
                    BestillingRepository.getAllBestilling(session)
                }

            assertSoftly {
                bestillingsBefore.size shouldBe 3
                bestillingsBefore.forExactly(1) { it.bestillingsbatchId!!.id shouldBe 1L }
                bestillingsBefore.forExactly(2) { it.bestillingsbatchId!!.id shouldBe 2L }

                updatedBatches.count { it.status == BestillingBatchStatus.Ny.value } shouldBe 1
                updatedBatches.count { it.status == BestillingBatchStatus.Ferdig.value } shouldBe 1
                bestillingsAfter.size shouldBe 2
                bestillingsAfter.forExactly(0) { it.bestillingsbatchId!!.id shouldBe 1L }
                bestillingsAfter.forExactly(2) { it.bestillingsbatchId!!.id shouldBe 2L }

                skattekort.size shouldBe 1
                val skattekortet = skattekort.first()
                skattekortet.identifikator shouldBe null
                skattekortet.forskuddstrekkList shouldContainExactly
                    listOf(
                        aForskuddstrekk("Prosentkort", Trekkode.LOENN_FRA_NAV, 15.70, null),
                        aForskuddstrekk("Prosentkort", Trekkode.UFOERETRYGD_FRA_NAV, 15.70, null),
                        aForskuddstrekk("Prosentkort", Trekkode.PENSJON_FRA_NAV, 13.00, null),
                    )
                skattekortet.tilleggsopplysningList.size shouldBe 1
                skattekortet.tilleggsopplysningList.forOne { it.opplysning shouldBe "oppholdPaaSvalbard" }
                skattekortet.resultatForSkattekort shouldBe ResultatForSkattekort.IkkeSkattekort
                skattekortet.kilde shouldBe SkattekortKilde.SYNTETISERT.value
            }
        }
    })

private fun hentSkattekortResponseFromFile(jsonfile: String): HentSkattekortResponse = Json.decodeFromString(HentSkattekortResponse.serializer(), Files.readString(Paths.get(jsonfile)))
