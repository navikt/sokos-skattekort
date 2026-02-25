package no.nav.sokos.skattekort.module.skattekort

import java.math.BigDecimal.valueOf
import java.math.RoundingMode
import java.time.LocalDateTime

import kotlin.time.ExperimentalTime
import kotlinx.serialization.json.Json

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.time.withConstantNow
import io.kotest.inspectors.forAll
import io.kotest.inspectors.forExactly
import io.kotest.inspectors.forOne
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.coEvery
import io.mockk.mockk
import org.slf4j.LoggerFactory

import no.nav.sokos.skattekort.config.TEAM_LOGS_MARKER
import no.nav.sokos.skattekort.infrastructure.UnleashIntegration
import no.nav.sokos.skattekort.infrastructure.skatteetaten.SkatteetatenClient
import no.nav.sokos.skattekort.infrastructure.skatteetaten.hentskattekort.Arbeidsgiver
import no.nav.sokos.skattekort.infrastructure.skatteetaten.hentskattekort.Arbeidsgiveridentifikator
import no.nav.sokos.skattekort.infrastructure.skatteetaten.hentskattekort.Arbeidstaker
import no.nav.sokos.skattekort.infrastructure.skatteetaten.hentskattekort.Forskuddstrekk
import no.nav.sokos.skattekort.infrastructure.skatteetaten.hentskattekort.HentSkattekortResponse
import no.nav.sokos.skattekort.infrastructure.skatteetaten.hentskattekort.Trekkprosent
import no.nav.sokos.skattekort.listener.DbListener
import no.nav.sokos.skattekort.module.forespoersel.Forsystem
import no.nav.sokos.skattekort.module.person.Audit
import no.nav.sokos.skattekort.module.person.AuditRepository
import no.nav.sokos.skattekort.module.person.AuditTag
import no.nav.sokos.skattekort.module.person.Person
import no.nav.sokos.skattekort.module.person.PersonId
import no.nav.sokos.skattekort.module.person.PersonRepository
import no.nav.sokos.skattekort.module.person.Personidentifikator
import no.nav.sokos.skattekort.module.utsending.Utsending
import no.nav.sokos.skattekort.module.utsending.UtsendingRepository
import no.nav.sokos.skattekort.skattekort.Prosentkort
import no.nav.sokos.skattekort.skattekort.ResponseStatus
import no.nav.sokos.skattekort.skattekort.ResultatForSkattekort.IkkeSkattekort
import no.nav.sokos.skattekort.skattekort.ResultatForSkattekort.IkkeTrekkplikt
import no.nav.sokos.skattekort.skattekort.ResultatForSkattekort.SkattekortopplysningerOK
import no.nav.sokos.skattekort.skattekort.ResultatForSkattekort.UgyldigFoedselsEllerDnummer
import no.nav.sokos.skattekort.skattekort.ResultatForSkattekort.UgyldigOrganisasjonsnummer
import no.nav.sokos.skattekort.skattekort.ResultatForSkattekort.UtgaattDnummerSkattekortForFoedselsnummerErLevert
import no.nav.sokos.skattekort.skattekort.Skattekort
import no.nav.sokos.skattekort.skattekort.SkattekortId
import no.nav.sokos.skattekort.skattekort.SkattekortKilde
import no.nav.sokos.skattekort.skattekort.SkattekortRepository
import no.nav.sokos.skattekort.skattekort.Tilleggsopplysning
import no.nav.sokos.skattekort.skattekort.Trekkode.LOENN_FRA_BIARBEIDSGIVER
import no.nav.sokos.skattekort.skattekort.Trekkode.LOENN_FRA_HOVEDARBEIDSGIVER
import no.nav.sokos.skattekort.skattekort.Trekkode.LOENN_FRA_NAV
import no.nav.sokos.skattekort.skattekort.Trekkode.PENSJON
import no.nav.sokos.skattekort.skattekort.Trekkode.PENSJON_FRA_NAV
import no.nav.sokos.skattekort.skattekort.Trekkode.UFOERETRYGD_FRA_NAV
import no.nav.sokos.skattekort.skattekort.Trekkode.UFOEREYTELSER_FRA_ANDRE
import no.nav.sokos.skattekort.skattekort.UgyldigOrganisasjonsnummerException
import no.nav.sokos.skattekort.skattekortbestilling.BestillingBatch
import no.nav.sokos.skattekort.skattekortbestilling.BestillingBatchRepository
import no.nav.sokos.skattekort.skattekortbestilling.BestillingBatchStatus
import no.nav.sokos.skattekort.skattekorthenting.Bestilling
import no.nav.sokos.skattekort.skattekorthenting.BestillingId
import no.nav.sokos.skattekort.skattekorthenting.BestillingRepository
import no.nav.sokos.skattekort.skattekorthenting.BestillingService
import no.nav.sokos.skattekort.utils.TestUtils.readFile
import no.nav.sokos.skattekort.utils.TestUtils.tx

@OptIn(ExperimentalTime::class)
class BestillingServiceTest :
    FunSpec({
        extensions(DbListener)

        val skatteetatenClient = mockk<SkatteetatenClient>()

        val bestillingService: BestillingService by lazy {
            BestillingService(
                dataSource = DbListener.dataSource,
                skatteetatenClient = skatteetatenClient,
                featureToggles = UnleashIntegration(),
            )
        }

        test("Logger som feil for ukjente personer fra henting av skattekort") {
            withConstantNow(LocalDateTime.parse("2025-12-20T00:00:00")) {
                val testAppender = ListAppender<ILoggingEvent>()
                val logger = LoggerFactory.getLogger(BestillingService::class.java) as Logger
                testAppender.start()
                logger.addAppender(testAppender)

                coEvery { skatteetatenClient.hentSkattekort(any(), any()) } returns
                    aHentSkattekortResponse(
                        aSkattekortFor("0101010000X", 10007),
                    )
                databaseHas(
                    aPerson(1L),
                    afoedselsnummer(personId = 1L, fnr = "01010100001"),
                    aPerson(2L),
                    afoedselsnummer(personId = 2L, fnr = "02020200002"),
                    aPerson(3L),
                    afoedselsnummer(personId = 3L, fnr = "03030300003"),
                    aBestillingsBatch(1L, "REF0001", "NY", "OPPDATERING"),
                )

                bestillingService.hentOppdaterteSkattekort()

                val person = tx { PersonRepository.findPersonByFnr(it, Personidentifikator("0101010000X")) }
                val batches: List<BestillingBatch> = tx(BestillingBatchRepository::list)
                val logEvents = testAppender.list

                assertSoftly {
                    person shouldBe null
                    batches shouldNotBeNull {
                        size shouldBe 1
                        first() shouldNotBeNull {
                            status shouldBe BestillingBatchStatus.Ferdig.value
                            type shouldBe "OPPDATERING"
                            bestillingsreferanse shouldBe "REF0001"
                        }
                    }
                    logEvents.shouldNotBeNull {
                        forOne {
                            it.level shouldBe Level.ERROR
                            it.message shouldContain "Fant ikke person for fnr"
                            it.markerList.shouldContain(TEAM_LOGS_MARKER)
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
                aPerson(1L),
                afoedselsnummer(personId = 1L, fnr = "01010100001"),
                anAbonnement(1L, personId = 1L, inntektsaar = 2025),
                aBestillingsBatch(1, "ref1", BestillingBatchStatus.Ny.value),
                aBestilling(1L, "01010100001", 2025, 1L),
            )

            bestillingService.hentBestillingsbatcher()

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
                aPerson(1L),
                afoedselsnummer(personId = 1L, fnr = "01010100001"),
                anAbonnement(1L, personId = 1L, inntektsaar = 2025),
                aBestillingsBatch(1, "ref1", BestillingBatchStatus.Ny.value),
                aBestilling(1L, "01010100001", 2025, 1L),
            )

            bestillingService.hentBestillingsbatcher()

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
                aPerson(1L),
                afoedselsnummer(personId = 1L, fnr = "01010100001"),
                anAbonnement(1L, personId = 1L, inntektsaar = 2025),
                aBestillingsBatch(1, "ref1", BestillingBatchStatus.Ny.value),
                aBestilling(1L, "01010100001", 2025, 1L),
            )

            bestillingService.hentBestillingsbatcher()

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
                aPerson(1L),
                afoedselsnummer(1L, "01010112345"),
                anAbonnement(1L, personId = 1L, inntektsaar = 2025),
                aBestillingsBatch(1, "BR1337", BestillingBatchStatus.Ny.value),
                aBestilling(1L, "01010112345", 2025, 1L),
            )

            bestillingService.hentBestillingsbatcher()

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
                aPerson(1L),
                afoedselsnummer(1L, "01010112345"),
                anAbonnement(1L, personId = 1L, inntektsaar = 2025),
                aBestillingsBatch(1, "BR1337", BestillingBatchStatus.Ny.value),
                aBestillingsBatch(2, "BR1338", BestillingBatchStatus.Ny.value),
                aBestilling(1L, "01010112345", 2025, 1L),
                aBestilling(1L, "23456789012", 2025, 2L),
            )

            bestillingService.hentBestillingsbatcher()

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

        test("henter skattekort med tomt frikort") {
            val response: HentSkattekortResponse = Json.decodeFromString(HentSkattekortResponse.serializer(), readFile("/skatteetaten/hentSkattekort/skattekortopplysningerOK_med_tomt_frikort.json"))
            coEvery { skatteetatenClient.hentSkattekort(any(), any()) } returns response

            databaseHas(
                aPerson(1L),
                afoedselsnummer(1L, "01010112345"),
                anAbonnement(1L, personId = 1L, inntektsaar = 2025),
                aBestillingsBatch(1, "BR1337", BestillingBatchStatus.Ny.value),
                aBestilling(1L, "01010112345", 2025, 1L),
            )

            bestillingService.hentBestillingsbatcher()

            val skattekort: List<Skattekort> = tx { SkattekortRepository.findAllByPersonId(it, PersonId(1), 2025, adminRole = true) }

            assertSoftly {
                skattekort shouldNotBeNull {
                    size shouldBe 1
                    last() shouldNotBeNull {
                        identifikator shouldBe "54407"
                        resultatForSkattekort shouldBe SkattekortopplysningerOK
                        forskuddstrekkList shouldContainExactly
                            listOf(
                                aForskuddstrekk("Frikort", UFOERETRYGD_FRA_NAV, frikortbeløp = null),
                                aForskuddstrekk("Frikort", UFOEREYTELSER_FRA_ANDRE, frikortbeløp = null),
                                aForskuddstrekk("Frikort", PENSJON_FRA_NAV, frikortbeløp = null),
                                aForskuddstrekk("Frikort", PENSJON, frikortbeløp = null),
                            )
                    }
                }
            }
        }

        test("henter skattekort med alle tilleggsopplysninger") {
            coEvery { skatteetatenClient.hentSkattekort(any(), any()) } returns
                aHentSkattekortResponse(
                    anArbeidstaker(
                        resultat = SkattekortopplysningerOK,
                        fnr = "01010100001",
                        inntektsaar = 2025,
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
                aPerson(1L),
                afoedselsnummer(personId = 1L, fnr = "01010100001"),
                anAbonnement(1L, personId = 1L, inntektsaar = 2025),
                aBestillingsBatch(1, "ref1", BestillingBatchStatus.Ny.value),
                aBestilling(1L, "01010100001", 2025, 1L),
            )

            bestillingService.hentBestillingsbatcher()

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
                aPerson(1L),
                afoedselsnummer(personId = 1L, fnr = "01010100001"),
                aPerson(2L),
                afoedselsnummer(personId = 2L, fnr = "02020200002"),
                aPerson(3L),
                afoedselsnummer(personId = 3L, fnr = "03030300003"),
                aPerson(4L),
                afoedselsnummer(personId = 4L, fnr = "04040400004"),
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

            bestillingService.hentBestillingsbatcher()
            bestillingService.hentBestillingsbatcher()

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
                        resultat = UgyldigFoedselsEllerDnummer,
                        fnr = "01010100001",
                        inntektsaar = 2025,
                    ),
                ) andThen
                aHentSkattekortResponse(
                    anArbeidstaker(
                        resultat = IkkeSkattekort,
                        fnr = "02020200002",
                        inntektsaar = 2025,
                    ),
                )

            databaseHas(
                aPerson(1L),
                afoedselsnummer(1L, "01010100001"),
                aBestillingsBatch(id = 1L, ref = "ref1", status = "NY"),
                aBestilling(personId = 1L, fnr = "01010100001", inntektsaar = 2025, batchId = 1L),
                aPerson(2L),
                afoedselsnummer(2L, "02020200002"),
                aBestillingsBatch(id = 2L, ref = "ref2", status = "NY"),
                aBestilling(personId = 2L, fnr = "02020200002", inntektsaar = 2025, batchId = 2L),
            )

            bestillingService.hentBestillingsbatcher()

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
                        resultatForSkattekort shouldBe UgyldigFoedselsEllerDnummer
                    }
                }

                skattekortPerson2 shouldNotBeNull {
                    size shouldBe 1
                    first() shouldNotBeNull {
                        identifikator shouldBe null
                        forskuddstrekkList shouldBe emptyList()
                        tilleggsopplysningList shouldBe emptyList()
                        resultatForSkattekort shouldBe IkkeSkattekort
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
                            Arbeidsgiver(
                                arbeidsgiveridentifikator =
                                    Arbeidsgiveridentifikator(
                                        organisasjonsnummer = "666",
                                    ),
                                arbeidstaker =
                                    listOf(
                                        anArbeidstaker(
                                            resultat = UgyldigOrganisasjonsnummer,
                                            fnr = "01010100001",
                                            inntektsaar = 2025,
                                        ),
                                    ),
                            ),
                        ),
                )

            databaseHas(
                aPerson(1L),
                afoedselsnummer(1L, "01010100001"),
                aBestillingsBatch(id = 1L, ref = "ref1", status = "NY"),
                aBestilling(personId = 1L, fnr = "01010100001", inntektsaar = 2025, batchId = 1L),
            )

            shouldThrow<UgyldigOrganisasjonsnummerException> {
                bestillingService.hentBestillingsbatcher()
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
                        inntektsaar = 2025,
                        tilleggsopplysninger =
                            listOf(
                                Tilleggsopplysning.fromValue("oppholdPaaSvalbard"),
                            ),
                    ),
                )
            databaseHas(
                aPerson(1L),
                afoedselsnummer(personId = 1L, fnr = "01010100001"),
                aPerson(2L),
                afoedselsnummer(personId = 2L, fnr = "02020200002"),
                aPerson(3L),
                afoedselsnummer(personId = 3L, fnr = "03030300003"),
                aBestillingsBatch(id = 1L, ref = "ref1", status = "NY"),
                aBestillingsBatch(id = 2L, ref = "ref2", status = "NY"),
                aBestilling(personId = 1L, fnr = "01010100001", inntektsaar = 2025, batchId = 1L),
                aBestilling(personId = 2L, fnr = "02020200002", inntektsaar = 2025, batchId = 2L),
                aBestilling(personId = 3L, fnr = "03030300003", inntektsaar = 2025, batchId = 2L),
            )

            bestillingService.hentBestillingsbatcher()

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
                        inntektsaar = 2025,
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
                aPerson(1L),
                afoedselsnummer(personId = 1L, fnr = "01010100001"),
                aBestillingsBatch(id = 1L, ref = "ref1", status = "NY"),
                aBestilling(personId = 1L, fnr = "01010100001", inntektsaar = 2025, batchId = 1L),
            )

            bestillingService.hentBestillingsbatcher()

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
                        inntektsaar = 2025,
                    ),
                )
            databaseHas(
                aPerson(1L),
                afoedselsnummer(personId = 1L, fnr = "01010100001"),
                aBestillingsBatch(id = 1L, ref = "ref1", status = "NY"),
                aBestilling(personId = 1L, fnr = "01010100001", inntektsaar = 2025, batchId = 1L),
            )

            bestillingService.hentBestillingsbatcher()

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

            bestillingService.hentBestillingsbatcher()

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
                aPerson(1L),
                afoedselsnummer(personId = 1L, fnr = "01010100001"),
                aPerson(2L),
                afoedselsnummer(personId = 2L, fnr = "02020200002"),
                aPerson(3L),
                afoedselsnummer(personId = 3L, fnr = "03030300003"),
                aBestillingsBatch(id = 1L, ref = "ref1", status = "NY"),
                aBestilling(personId = 1L, fnr = "01010100001", inntektsaar = 2025, batchId = 1L),
                aBestilling(personId = 2L, fnr = "02020200002", inntektsaar = 2025, batchId = 1L),
                aBestilling(personId = 3L, fnr = "03030300003", inntektsaar = 2025, batchId = 1L),
            )

            shouldThrow<RuntimeException> {
                bestillingService.hentBestillingsbatcher()
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
            coEvery { skatteetatenClient.hentSkattekort(any(), any()) } returns aHentSkattekortResponse(anArbeidstaker(resultat = IkkeSkattekort, fnr = "02020200002", inntektsaar = 2025))

            databaseHas(
                aPerson(1L),
                afoedselsnummer(personId = 1L, fnr = "01010100001"),
                aPerson(2L),
                afoedselsnummer(personId = 2L, fnr = "02020200002"),
                aPerson(3L),
                afoedselsnummer(personId = 3L, fnr = "03030300003"),
                aBestillingsBatch(id = 1L, ref = "ref1", status = "FEILET"),
                aBestillingsBatch(id = 2L, ref = "ref2", status = "NY"),
                aBestilling(personId = 1L, fnr = "01010100001", inntektsaar = 2025, batchId = 1L),
                aBestilling(personId = 2L, fnr = "02020200002", inntektsaar = 2025, batchId = 2L),
            )

            bestillingService.hentBestillingsbatcher()

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

        test("Vi skal kunne parse skattekort med utløpt d-nummer") {
            val arbeidstaker =
                Json.decodeFromString<Arbeidstaker>(
                    """        
                            {
                              "arbeidstakeridentifikator": "67853500256",
                              "resultatForSkattekort": "utgaattDnummerSkattekortForFoedselsnummerErLevert",
                              "skattekort": {
                                "utstedtDato": "2025-10-16",
                                "skattekortidentifikator": 53112,
                                "forskuddstrekk": [
                                  {
                                    "trekkode": "pensjon",
                                    "trekkprosent": {
                                      "prosentsats": 36,
                                      "antallMaanederForTrekk": 11
                                    }
                                  },
                                  {
                                    "trekkode": "pensjonFraNAV",
                                    "trekkprosent": {
                                      "prosentsats": 36,
                                      "antallMaanederForTrekk": 11
                                    }
                                  }
                                ]
                              },
                              "inntektsaar": 2025
                            }
                            """,
                )
            val skattekort = Skattekort(PersonId(0), arbeidstaker)
            skattekort.forskuddstrekkList shouldHaveSize 2
        }

        test("UtgaattDnummerSkattekortForFoedselsnummerErLevert skal opprette ny bestilling med gyldig fnr") {
            val dnr = "41010100001"
            val fnr = "01010112345"
            coEvery { skatteetatenClient.hentSkattekort(any(), any()) } returns
                aHentSkattekortResponse(
                    anArbeidstaker(
                        resultat = UtgaattDnummerSkattekortForFoedselsnummerErLevert,
                        fnr = dnr,
                        inntektsaar = 2025,
                    ),
                ) andThen aHentSkattekortResponse(aSkattekortFor(fnr = fnr, id = 1L))

            databaseHas(
                aPerson(personId = 1L),
                afoedselsnummer(personId = 1L, fnr = dnr),
                anAbonnement(forespoerselId = 1L, personId = 1L, inntektsaar = 2025),
                aBestillingsBatch(id = 1L, ref = "ref1", status = "NY"),
                aBestilling(personId = 1L, fnr = dnr, inntektsaar = 2025, batchId = 1L),
                // Oppdatert foedselsnummer
                afoedselsnummer(personId = 1L, fnr = fnr),
            )

            bestillingService.hentBestillingsbatcher()

            val updatedBatches: List<BestillingBatch> = tx(BestillingBatchRepository::list)
            var skattekort: List<Skattekort> =
                tx {
                    SkattekortRepository.findAllByPersonId(it, PersonId(1), 2025, adminRole = false)
                }
            val bestillingsAfter: List<Bestilling> = tx(BestillingRepository::getBestillingsKandidaterForBatch)
            var utsendinger = tx(UtsendingRepository::getAllUtsendinger)

            assertSoftly {
                updatedBatches.count { it.status == BestillingBatchStatus.Ferdig.value } shouldBe 1

                bestillingsAfter shouldNotBeNull {
                    size shouldBe 1
                    first().fnr.value shouldBe fnr
                    first().personId.value shouldBe 1L
                    first().inntektsaar shouldBe 2025
                }

                skattekort shouldNotBeNull {
                    size shouldBe 1
                    last() shouldNotBeNull {
                        kilde shouldBe SkattekortKilde.SKATTEETATEN.value
                        resultatForSkattekort shouldBe UtgaattDnummerSkattekortForFoedselsnummerErLevert
                        utstedtDato shouldBe null
                        identifikator shouldBe null
                        forskuddstrekkList shouldBe emptyList()
                    }
                }
                utsendinger shouldBe emptyList()
            }

            // Kjør hent skattekort på nytt
            databaseHas(
                aBestillingsBatch(id = 2L, ref = "ref1", status = "NY"),
            )

            bestillingService.hentBestillingsbatcher()

            skattekort = tx { SkattekortRepository.findAllByPersonId(it, PersonId(1), 2025, adminRole = false) }
            utsendinger = tx(UtsendingRepository::getAllUtsendinger)

            skattekort shouldNotBeNull {
                size shouldBe 2
            }
            utsendinger shouldNotBeNull {
                size shouldBe 1
                first().fnr.value shouldBe dnr
                first().forsystem shouldBe Forsystem.OPPDRAGSSYSTEMET
                first().inntektsaar shouldBe 2025
            }
        }
    })

fun aSkattekortFor(
    fnr: String,
    id: Long,
) = anArbeidstaker(
    resultat = SkattekortopplysningerOK,
    fnr = fnr,
    inntektsaar = 2025,
    skattekort =
        no.nav.sokos.skattekort.infrastructure.skatteetaten.hentskattekort.Skattekort(
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
