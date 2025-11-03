package no.nav.sokos.skattekort.module.skattekort

import java.nio.file.Files
import java.nio.file.Paths

import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.FunSpec
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

        coEvery { personService.findOrCreatePersonByFnr(any(), any(), any(), any()) } returns
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

        val bestillingsService: BestillingsService by lazy {
            BestillingsService(DbListener.dataSource, skatteetatenClient, personService)
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

        test("henter skattekort for batch") {

            coEvery { skatteetatenClient.hentSkattekort(any()) } returns hentSkattekortResponseFromFile("src/test/resources/skatteetaten/skattekortopplysningerOK.json")

            // Sett inn bestillinger uten bestillingsbatch.
            DbListener.loadDataSet("database/person/persondata.sql")
            DbListener.loadDataSet("database/bestillinger/bestillinger.sql")
            DbListener.loadDataSet("database/bestillinger/abonnementer.sql")

            val bestillingsBefore: List<Bestilling> =
                DbListener.dataSource.transaction { session ->
                    BestillingRepository.getAllBestilling(session)
                }

            bestillingsService.hentSkattekort()

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
                updatedBatches.count { it.status == "NY" } shouldBe 1
                updatedBatches.count { it.status == "FERDIG" } shouldBe 1
                bestillingsBefore.size shouldBe 3
                bestillingsBefore
                    .filter {
                        it.bestillingsbatchId == BestillingsbatchId(1L)
                    }.size shouldBe 2
                bestillingsBefore
                    .filter {
                        it.bestillingsbatchId == BestillingsbatchId(2L)
                    }.size shouldBe 1
                bestillingsAfter.size shouldBe 1
                bestillingsAfter
                    .filter {
                        it.bestillingsbatchId == BestillingsbatchId(1L)
                    }.size shouldBe 0
                bestillingsAfter
                    .filter {
                        it.bestillingsbatchId == BestillingsbatchId(2L)
                    }.size shouldBe 1
                skattekort.size shouldBe 1
                skattekort.first().identifikator shouldBe "54407"
                skattekort.first().forskuddstrekkList.size shouldBe 5
                skattekort.first().tilleggsopplysningList.size shouldBe 4
                skattekort.first().tilleggsopplysningList shouldContainExactlyInAnyOrder
                    listOf(
                        "oppholdPaaSvalbard",
                        "kildeskattPaaPensjon",
                        "oppholdITiltakssone",
                        "kildeskattPaaLoenn",
                    ).map { Tilleggsopplysning(it) }
                utsendingerAfter.size shouldBe 1
            }
        }

        test("ugyldigFoedselsEllerDnummer") {
            coEvery { skatteetatenClient.hentSkattekort(any()) } returns hentSkattekortResponseFromFile("src/test/resources/skatteetaten/ugyldigFoedselsEllerDnummer.json")
            DbListener.loadDataSet("database/person/persondata.sql")
            DbListener.loadDataSet("database/bestillinger/bestillinger.sql")

            val bestillingsBefore: List<Bestilling> =
                DbListener.dataSource.transaction { session ->
                    BestillingRepository.getAllBestilling(session)
                }

            bestillingsService.hentSkattekort()

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
                updatedBatches.count { it.status == "NY" } shouldBe 1
                updatedBatches.count { it.status == "FERDIG" } shouldBe 1
                bestillingsBefore.size shouldBe 3
                bestillingsBefore
                    .filter {
                        it.bestillingsbatchId == BestillingsbatchId(1L)
                    }.size shouldBe 2
                bestillingsBefore
                    .filter {
                        it.bestillingsbatchId == BestillingsbatchId(2L)
                    }.size shouldBe 1

                bestillingsAfter.size shouldBe 1
                bestillingsAfter
                    .filter {
                        it.bestillingsbatchId == BestillingsbatchId(1L)
                    }.size shouldBe 0
                bestillingsAfter
                    .filter {
                        it.bestillingsbatchId == BestillingsbatchId(2L)
                    }.size shouldBe 1
                skattekort.size shouldBe 1
                skattekort.first().identifikator shouldBe null
                skattekort.first().forskuddstrekkList shouldBe emptyList()
                skattekort.first().tilleggsopplysningList shouldBe emptyList()
                skattekort.first().resultatForSkattekort shouldBe ResultatForSkattekort.UgyldigFoedselsEllerDnummer
            }
        }

        test("ikkeSkattekort") {
            coEvery { skatteetatenClient.hentSkattekort(any()) } returns hentSkattekortResponseFromFile("src/test/resources/skatteetaten/ikkeSkattekort.json")
            DbListener.loadDataSet("database/person/persondata.sql")
            DbListener.loadDataSet("database/bestillinger/bestillinger.sql")

            val bestillingsBefore: List<Bestilling> =
                DbListener.dataSource.transaction { session ->
                    BestillingRepository.getAllBestilling(session)
                }

            bestillingsService.hentSkattekort()

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
                updatedBatches.count { it.status == "NY" } shouldBe 1
                updatedBatches.count { it.status == "FERDIG" } shouldBe 1
                bestillingsBefore.size shouldBe 3
                bestillingsBefore
                    .filter {
                        it.bestillingsbatchId == BestillingsbatchId(1L)
                    }.size shouldBe 2
                bestillingsBefore
                    .filter {
                        it.bestillingsbatchId == BestillingsbatchId(2L)
                    }.size shouldBe 1

                bestillingsAfter.size shouldBe 1
                bestillingsAfter
                    .filter {
                        it.bestillingsbatchId == BestillingsbatchId(1L)
                    }.size shouldBe 0
                bestillingsAfter
                    .filter {
                        it.bestillingsbatchId == BestillingsbatchId(2L)
                    }.size shouldBe 1
                skattekort.size shouldBe 1
                skattekort.first().identifikator shouldBe null
                skattekort.first().forskuddstrekkList.size shouldBe 2

                skattekort.first().tilleggsopplysningList shouldBe emptyList()
                skattekort.first().resultatForSkattekort shouldBe ResultatForSkattekort.IkkeSkattekort
            }
        }
    })

private fun hentSkattekortResponseFromFile(jsonfile: String): HentSkattekortResponse = Json.decodeFromString(HentSkattekortResponse.serializer(), Files.readString(Paths.get(jsonfile)))
