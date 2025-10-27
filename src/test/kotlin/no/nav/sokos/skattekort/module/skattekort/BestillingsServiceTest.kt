package no.nav.sokos.skattekort.module.skattekort

import java.nio.file.Files
import java.nio.file.Paths

import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.FunSpec
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
import no.nav.sokos.skattekort.skatteetaten.SkatteetatenBestillSkattekortResponse
import no.nav.sokos.skattekort.skatteetaten.SkatteetatenClient
import no.nav.sokos.skattekort.skatteetaten.svar.Root
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

        test("henter skattekort for batch") {
            val bestillingsreferanse = "some-bestillings-ref"

            val jsonPath = Paths.get("src/test/resources/skatteetaten/skattekortopplysningerOK.json")
            val jsonString = Files.readString(jsonPath)
            val rootObj: Root = Json.decodeFromString(Root.serializer(), jsonString)

            coEvery { skatteetatenClient.hentSkattekort(bestillingsreferanse) } returns rootObj

            // Sett inn bestillinger uten bestillingsbatch.
            DbListener.loadDataSet("database/person/persondata.sql")
            val fnr1 = "12345678901"
            val fnr2 = "12345678902"
            val fnr3 = "12345678903"
            DbListener.dataSource.transaction { session ->
                session.run(
                    queryOf(
                        """
                        INSERT INTO bestillingsbatcher(id, bestillingsreferanse, data_sendt)
                        VALUES (1, '$bestillingsreferanse', '{}'), (2, 'other-ref', '{}');
                        INSERT INTO bestillinger(person_id, fnr, inntektsaar, bestillingsbatch_id)
                        VALUES (1, '$fnr1', 2025, 1),
                               (2, '$fnr2', 2025, 1),
                               (3, '$fnr3', 2025, 2);
                        """.trimIndent(),
                    ).asExecute,
                )
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

            println("bestillingsreferanse = $bestillingsreferanse")
        }
    })
