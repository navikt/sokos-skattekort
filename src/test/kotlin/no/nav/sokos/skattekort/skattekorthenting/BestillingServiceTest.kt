package no.nav.sokos.skattekort.skattekorthenting

import java.time.LocalDateTime

import kotlin.time.ExperimentalTime

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.github.resilience4j.circuitbreaker.CallNotPermittedException
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.time.withConstantNow
import io.kotest.inspectors.forAll
import io.kotest.inspectors.forExactly
import io.kotest.inspectors.forOne
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.coEvery
import io.mockk.mockk
import org.slf4j.LoggerFactory

import no.nav.sokos.skattekort.config.TEAM_LOGS_MARKER
import no.nav.sokos.skattekort.infrastructure.UnleashIntegration
import no.nav.sokos.skattekort.infrastructure.skatteetaten.SkatteetatenClient
import no.nav.sokos.skattekort.infrastructure.skatteetaten.SkatteetatenClientTestUtils.aSkattekortFor
import no.nav.sokos.skattekort.listener.DbListener
import no.nav.sokos.skattekort.person.Audit
import no.nav.sokos.skattekort.person.AuditRepository
import no.nav.sokos.skattekort.person.AuditTag
import no.nav.sokos.skattekort.person.Person
import no.nav.sokos.skattekort.person.PersonId
import no.nav.sokos.skattekort.person.PersonRepository
import no.nav.sokos.skattekort.person.Personidentifikator
import no.nav.sokos.skattekort.skattekort.ResponseStatus
import no.nav.sokos.skattekort.skattekort.ResultatForSkattekort
import no.nav.sokos.skattekort.skattekort.ResultatForSkattekort.IkkeSkattekort
import no.nav.sokos.skattekort.skattekort.SkattekortRepository
import no.nav.sokos.skattekort.skattekort.aBestilling
import no.nav.sokos.skattekort.skattekort.aBestillingsbatch
import no.nav.sokos.skattekort.skattekort.aHentSkattekortResponse
import no.nav.sokos.skattekort.skattekort.aPerson
import no.nav.sokos.skattekort.skattekort.afoedselsnummer
import no.nav.sokos.skattekort.skattekort.anAbonnement
import no.nav.sokos.skattekort.skattekort.anArbeidstaker
import no.nav.sokos.skattekort.skattekort.databaseHas
import no.nav.sokos.skattekort.skattekortbestilling.Bestillingsbatch
import no.nav.sokos.skattekort.skattekortbestilling.BestillingsbatchStatus.FEILET
import no.nav.sokos.skattekort.skattekortbestilling.BestillingsbatchStatus.FERDIG
import no.nav.sokos.skattekort.skattekortbestilling.BestillingsbatchStatus.NY
import no.nav.sokos.skattekort.skattekortbestilling.BestillingsbatchStatus.RETRY
import no.nav.sokos.skattekort.skattekortbestilling.BestillingsbatchType
import no.nav.sokos.skattekort.skattekortbestilling.BestillingsbatchType.OPPDATERING
import no.nav.sokos.skattekort.skattekortdata.SkattekortDataRepository
import no.nav.sokos.skattekort.utils.DBTestUtils
import no.nav.sokos.skattekort.utils.TestUtils.tx

@OptIn(ExperimentalTime::class)
class BestillingServiceTest :
    FunSpec({
        extensions(DbListener)

        val logger = LoggerFactory.getLogger(BestillingService::class.java) as Logger
        val testAppender =
            ListAppender<ILoggingEvent>().apply {
                start()
                logger.addAppender(this)
            }

        val skatteetatenClient = mockk<SkatteetatenClient>()

        val bestillingService: BestillingService by lazy {
            BestillingService(
                dataSource = DbListener.dataSource,
                skatteetatenClient = skatteetatenClient,
                featureToggles = UnleashIntegration(),
            )
        }

        afterEach {
            testAppender.list.clear()
        }

        test("henteBestillingsbatcher enkleste scenario") {
            databaseHas(
                aPerson(1L),
                afoedselsnummer(personId = 1L, fnr = "01010100001"),
                anAbonnement(1L, personId = 1L, inntektsaar = 2025),
                aBestillingsbatch(1, "ref1", NY),
                aBestilling(1L, "01010100001", 2025, 1L),
            )

            coEvery { skatteetatenClient.hentSkattekort(any()) } returns
                aHentSkattekortResponse(aSkattekortFor("01010100001", 10001))
            bestillingService.hentBestillingsbatcher(BestillingsbatchType.BESTILLING)

            val bestilingsbatchList = tx(DBTestUtils::getAllBestillingsbatch)
            val skattekortList = tx { SkattekortRepository.findAllByPersonId(it, PersonId(1), 2025, adminRole = false) }
            val bestilliingListAfter = tx(DBTestUtils::getAllBestilling)
            val skattekortDataList = tx { SkattekortDataRepository.getUnprocessedSkattekortData(it) }

            assertSoftly {
                bestilingsbatchList shouldNotBeNull {
                    size shouldBe 1
                    first() shouldNotBeNull {
                        status shouldBe FERDIG
                    }
                }
                skattekortDataList.size shouldBe 1
                skattekortList shouldBe emptyList()
                bestilliingListAfter shouldBe emptyList()
            }
        }

        test("Logger som feil for ukjente personer fra henteBestillingsbatcher") {
            withConstantNow(LocalDateTime.parse("2025-12-20T00:00:00")) {
                coEvery { skatteetatenClient.hentSkattekort(any()) } returns
                    aHentSkattekortResponse(aSkattekortFor("0101010000X", 10007))

                databaseHas(
                    aPerson(1L),
                    afoedselsnummer(personId = 1L, fnr = "01010100001"),
                    aPerson(2L),
                    afoedselsnummer(personId = 2L, fnr = "02020200002"),
                    aPerson(3L),
                    afoedselsnummer(personId = 3L, fnr = "03030300003"),
                    aBestillingsbatch(1L, "REF0001", NY, OPPDATERING),
                )

                bestillingService.hentBestillingsbatcher(OPPDATERING)

                val person = tx { PersonRepository.findPersonByFnr(it, Personidentifikator("0101010000X")) }
                val bestillingsbatchList = tx(DBTestUtils::getAllBestillingsbatch)

                assertSoftly {
                    person shouldBe null
                    bestillingsbatchList shouldNotBeNull {
                        size shouldBe 1
                        first() shouldNotBeNull {
                            status shouldBe FERDIG
                            type shouldBe OPPDATERING
                            bestillingsreferanse shouldBe "REF0001"
                        }
                    }
                    testAppender.list.shouldNotBeNull {
                        forOne {
                            it.level shouldBe Level.ERROR
                            it.message shouldContain "Fant ikke person for fnr"
                            it.markerList.shouldContain(TEAM_LOGS_MARKER)
                        }
                    }
                }
            }
        }

        test("hentBestillingsbatcher, ingen endring-respons") {
            coEvery { skatteetatenClient.hentSkattekort(any()) } returns
                aHentSkattekortResponse(response = ResponseStatus.INGEN_ENDRINGER)

            databaseHas(
                aPerson(1L),
                afoedselsnummer(personId = 1L, fnr = "01010100001"),
                anAbonnement(1L, personId = 1L, inntektsaar = 2025),
                aBestillingsbatch(1, "ref1", NY),
                aBestilling(1L, "01010100001", 2025, 1L),
            )

            bestillingService.hentBestillingsbatcher(BestillingsbatchType.BESTILLING)
            val bestillingsbatchList = tx(DBTestUtils::getAllBestillingsbatch)

            assertSoftly {
                bestillingsbatchList shouldNotBeNull {
                    size shouldBe 1
                    first() shouldNotBeNull {
                        status shouldBe FERDIG
                    }
                }
            }
        }

        test("hentBestillingsbatcher, ugyldig inntektsaar returneres") {
            coEvery { skatteetatenClient.hentSkattekort(any()) } returns
                aHentSkattekortResponse(
                    response = ResponseStatus.UGYLDIG_INNTEKTSAAR,
                )

            databaseHas(
                aPerson(1L),
                afoedselsnummer(personId = 1L, fnr = "01010100001"),
                anAbonnement(1L, personId = 1L, inntektsaar = 2025),
                aBestillingsbatch(1, "ref1", NY),
                aBestilling(1L, "01010100001", 2025, 1L),
            )

            bestillingService.hentBestillingsbatcher(BestillingsbatchType.BESTILLING)

            val updatedBatches: List<Bestillingsbatch> = tx(DBTestUtils::getAllBestillingsbatch)

            assertSoftly {
                updatedBatches shouldNotBeNull {
                    size shouldBe 1
                    first() shouldNotBeNull {
                        status shouldBe FEILET
                    }
                }
            }
        }

        test("hentBestillingsbatcher håndterer alle batcher") {

            coEvery { skatteetatenClient.hentSkattekort(any()) } returns
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
                aBestillingsbatch(1, "ref1", NY),
                aBestillingsbatch(2, "ref2", NY),
                aBestilling(1L, "01010100001", 2025, 1L),
                aBestilling(2L, "02020200002", 2025, 2L),
                aBestilling(3L, "02020200003", 2025, 2L), // NB: også batch 2
                aBestilling(4L, "04040400004", 2025, null),
            )

            bestillingService.hentBestillingsbatcher(BestillingsbatchType.BESTILLING)
            bestillingService.hentBestillingsbatcher(BestillingsbatchType.BESTILLING)

            val updatedBatches: List<Bestillingsbatch> = tx(DBTestUtils::getAllBestillingsbatch)
            val skattekortDataList = tx { SkattekortDataRepository.getUnprocessedSkattekortData(it) }

            assertSoftly("Etter første kjøring skal alle batchene være Ferdig") {
                updatedBatches shouldNotBeNull {
                    size shouldBe 2
                    forOne {
                        it.id!!.id shouldBe 1L
                        it.status shouldBe FERDIG
                    }
                    forOne {
                        it.id!!.id shouldBe 2L
                        it.status shouldBe FERDIG
                    }
                }
                skattekortDataList.size shouldBe 3
            }
        }

        test("plukker ikke opp batch med status FEILET, gjør ingenting og trenger ikke mer data") {
            databaseHas(aBestillingsbatch(id = 1L, ref = "some-ref", status = FEILET))

            bestillingService.hentBestillingsbatcher(BestillingsbatchType.BESTILLING)

            val bestillingsbatchList = tx(DBTestUtils::getAllBestillingsbatch)
            val auditAfter = tx { AuditRepository.getAuditByPersonId(it, PersonId(1L)) }

            assertSoftly {
                bestillingsbatchList shouldNotBeNull {
                    size shouldBe 1
                    forExactly(1) { it.status shouldBe FEILET }
                }
                auditAfter shouldBe emptyList()
            }
        }

        test("plukker opp batch med status NY, får 404 fra skatt") {
            databaseHas(
                aPerson(1L),
                afoedselsnummer(personId = 1L, fnr = "01010100001"),
                aPerson(2L),
                afoedselsnummer(personId = 2L, fnr = "02020200002"),
                aPerson(3L),
                afoedselsnummer(personId = 3L, fnr = "03030300003"),
                aBestillingsbatch(id = 1L, ref = "ref1", status = NY),
                aBestilling(personId = 1L, fnr = "01010100001", inntektsaar = 2025, batchId = 1L),
                aBestilling(personId = 2L, fnr = "02020200002", inntektsaar = 2025, batchId = 1L),
                aBestilling(personId = 3L, fnr = "03030300003", inntektsaar = 2025, batchId = 1L),
            )

            coEvery { skatteetatenClient.hentSkattekort(any()) } throws RuntimeException("Feil ved henting av skattekort: 404")
            bestillingService.hentBestillingsbatcher(BestillingsbatchType.BESTILLING)

            val bestillingsbatchList = tx(DBTestUtils::getAllBestillingsbatch)
            val auditPerson1: List<Audit> = tx { AuditRepository.getAuditByPersonId(it, PersonId(1L)) }
            val auditPerson2: List<Audit> = tx { AuditRepository.getAuditByPersonId(it, PersonId(2L)) }
            val auditPerson3: List<Audit> = tx { AuditRepository.getAuditByPersonId(it, PersonId(3L)) }
            val bestillingListAfter = tx(DBTestUtils::getAllBestilling)

            assertSoftly {
                withClue("Should mark batch as RETRY") {
                    bestillingsbatchList shouldNotBeNull {
                        first().status shouldBe RETRY
                    }
                }

                withClue("Should not delete bestilling or remove batch association") {
                    bestillingListAfter shouldNotBeNull {
                        size shouldBe 3
                        forAll {
                            it.bestillingsbatchId!!.id shouldBe 1L
                        }
                    }
                }

                withClue("Should create auditlog for all persons in batch") {
                    (auditPerson1 + auditPerson2 + auditPerson3) shouldNotBeNull {
                        forAll {
                            it.tag shouldBe AuditTag.HENTING_AV_SKATTEKORT_FEILET
                        }
                    }
                }

                testAppender.list
                    .map { it.level to it.message }
                    .shouldContainExactlyInAnyOrder(
                        listOf(
                            Level.INFO to "Henter skattekort for ref1",
                            Level.INFO to "Henting av skattekort for batch: 1, type: BESTILLING feilet, men prøvd på nytt senere",
                        ),
                    )
            }
        }

        test("plukker ikke opp batch med status FEILET men tar den andre istedenfor") {
            coEvery { skatteetatenClient.hentSkattekort(any()) } returns
                aHentSkattekortResponse(
                    anArbeidstaker(
                        resultat = IkkeSkattekort,
                        fnr = "02020200002",
                        inntektsaar = 2025,
                    ),
                )

            databaseHas(
                aPerson(1L),
                afoedselsnummer(personId = 1L, fnr = "01010100001"),
                aPerson(2L),
                afoedselsnummer(personId = 2L, fnr = "02020200002"),
                aPerson(3L),
                afoedselsnummer(personId = 3L, fnr = "03030300003"),
                aBestillingsbatch(id = 1L, ref = "ref1", status = FEILET),
                aBestillingsbatch(id = 2L, ref = "ref2", status = NY),
                aBestilling(personId = 1L, fnr = "01010100001", inntektsaar = 2025, batchId = 1L),
                aBestilling(personId = 2L, fnr = "02020200002", inntektsaar = 2025, batchId = 2L),
            )

            bestillingService.hentBestillingsbatcher(BestillingsbatchType.BESTILLING)

            val updatedBatches: List<Bestillingsbatch> = tx(DBTestUtils::getAllBestillingsbatch)
            val bestillingsAfter: List<Bestilling> = tx(DBTestUtils::getAllBestilling)
            val person: Person = tx { PersonRepository.findPersonById(it, PersonId(1L)) }

            assertSoftly {
                bestillingsAfter shouldNotBeNull {
                    size shouldBe 1
                    first().bestillingsbatchId!!.id shouldBe 1L
                }

                updatedBatches shouldNotBeNull {
                    first().status shouldBe FEILET
                    last().status shouldBe FERDIG
                }
                person shouldNotBeNull {
                    flagget shouldBe false
                }
            }
        }

        test("UgyldigOrganisasjonsnummer skal markere batch som FEILET og auditlogger") {
            coEvery { skatteetatenClient.hentSkattekort(any()) } returns
                aHentSkattekortResponse(
                    anArbeidstaker(
                        resultat = ResultatForSkattekort.UgyldigOrganisasjonsnummer,
                        fnr = "01010100001",
                        inntektsaar = 2025,
                    ),
                )

            databaseHas(
                aPerson(1L),
                afoedselsnummer(personId = 1L, fnr = "01010100001"),
                anAbonnement(1L, personId = 1L, inntektsaar = 2025),
                aBestillingsbatch(1L, "ref1", NY),
                aBestilling(1L, "01010100001", 2025, 1L),
            )

            bestillingService.hentBestillingsbatcher(BestillingsbatchType.BESTILLING)

            val bestillingsbatchList = tx(DBTestUtils::getAllBestillingsbatch)
            val audit: List<Audit> = tx { AuditRepository.getAuditByPersonId(it, PersonId(1L)) }
            val bestillingListAfter = tx(DBTestUtils::getAllBestilling)

            assertSoftly {
                bestillingsbatchList shouldNotBeNull {
                    size shouldBe 1
                    first().status shouldBe FEILET
                }

                audit shouldNotBeNull {
                    size shouldBe 1
                    first().tag shouldBe AuditTag.HENTING_AV_SKATTEKORT_FEILET
                }

                bestillingListAfter shouldNotBeNull {
                    size shouldBe 1
                    first().bestillingsbatchId?.id shouldBe 1L
                }

                testAppender.list
                    .map { it.level to it.message }
                    .shouldContainExactlyInAnyOrder(
                        listOf(
                            Level.INFO to "Henter skattekort for ref1",
                            Level.INFO to "Ved henting av skattekort for batch 1 returnerte Skatteetaten FORESPOERSEL_OK",
                            Level.ERROR to "Ugydlig organisasjonsnummer av skattekort for batch 1 feilet. Ugyldig organisasjonsnummer",
                            Level.ERROR to "Henting av skattekort for batch 1 feilet med Ugyldig organisasjonsnummer, sjekk TEAM LOGS for detaljer",
                        ),
                    )
            }
        }

        test("UgyldigFoedselsEllerDnummer skal markere batch som FERDIG og auditlogger") {
            coEvery { skatteetatenClient.hentSkattekort(any()) } returns
                aHentSkattekortResponse(
                    anArbeidstaker(
                        resultat = ResultatForSkattekort.UgyldigFoedselsEllerDnummer,
                        fnr = "01010100001",
                        inntektsaar = 2025,
                    ),
                )

            databaseHas(
                aPerson(1L),
                afoedselsnummer(personId = 1L, fnr = "01010100001"),
                anAbonnement(1L, personId = 1L, inntektsaar = 2025),
                aBestillingsbatch(1L, "ref1", NY),
                aBestilling(1L, "01010100001", 2025, 1L),
            )

            bestillingService.hentBestillingsbatcher(BestillingsbatchType.BESTILLING)

            val bestillingsbatchList = tx(DBTestUtils::getAllBestillingsbatch)
            val audit: List<Audit> = tx { AuditRepository.getAuditByPersonId(it, PersonId(1L)) }
            val bestillingListAfter = tx(DBTestUtils::getAllBestilling)
            val person: Person = tx { PersonRepository.findPersonById(it, PersonId(1L)) }
            val skattekortDataList = tx { SkattekortDataRepository.getUnprocessedSkattekortData(it) }

            assertSoftly {
                bestillingsbatchList shouldNotBeNull {
                    size shouldBe 1
                    first().status shouldBe FERDIG
                }

                audit shouldNotBeNull {
                    size shouldBe 1
                    first().tag shouldBe AuditTag.INVALID_FNR
                }

                bestillingListAfter shouldBe emptyList()

                person shouldNotBeNull {
                    flagget shouldBe true
                }

                skattekortDataList.size shouldBe 1

                testAppender.list
                    .map { it.level to it.message }
                    .shouldContainExactlyInAnyOrder(
                        listOf(
                            Level.INFO to "Henter skattekort for ref1",
                            Level.INFO to "Ved henting av skattekort for batch 1 returnerte Skatteetaten FORESPOERSEL_OK",
                            Level.INFO to "Bestillingsbatch 1 ferdig behandlet med mottatte brukere",
                        ),
                    )
            }
        }

        test("hentBestillingsbatcher når Skatteetaten-svar ikke er klart ennå (null), skal ikke oppdatere batch") {
            databaseHas(
                aPerson(1L),
                afoedselsnummer(personId = 1L, fnr = "01010100001"),
                anAbonnement(1L, personId = 1L, inntektsaar = 2025),
                aBestillingsbatch(1, "ref1", NY),
                aBestilling(1L, "01010100001", 2025, 1L),
            )

            coEvery { skatteetatenClient.hentSkattekort(any()) } returns null

            bestillingService.hentBestillingsbatcher(BestillingsbatchType.BESTILLING)

            val bestillingsbatchList = tx(DBTestUtils::getAllBestillingsbatch)
            val auditAfter = tx { AuditRepository.getAuditByPersonId(it, PersonId(1L)) }
            val bestillingListAfter = tx(DBTestUtils::getAllBestilling)

            assertSoftly {
                bestillingsbatchList shouldNotBeNull {
                    size shouldBe 1
                    first().status shouldBe NY
                }
                auditAfter shouldBe emptyList()
                bestillingListAfter shouldNotBeNull {
                    size shouldBe 1
                    first().bestillingsbatchId?.id shouldBe 1L
                }
            }
        }

        test("hentBestillingsbatcher når circuit breaker er åpen (CallNotPermittedException), skal la batch stå urørt") {
            databaseHas(
                aPerson(1L),
                afoedselsnummer(personId = 1L, fnr = "01010100001"),
                anAbonnement(1L, personId = 1L, inntektsaar = 2025),
                aBestillingsbatch(1, "ref1", NY),
                aBestilling(1L, "01010100001", 2025, 1L),
            )

            val circuitBreaker = CircuitBreaker.of("skatteetaten", CircuitBreakerConfig.ofDefaults())
            coEvery { skatteetatenClient.hentSkattekort(any()) } throws CallNotPermittedException.createCallNotPermittedException(circuitBreaker)

            bestillingService.hentBestillingsbatcher(BestillingsbatchType.BESTILLING)

            val bestillingsbatchList = tx(DBTestUtils::getAllBestillingsbatch)
            val auditAfter = tx { AuditRepository.getAuditByPersonId(it, PersonId(1L)) }

            assertSoftly {
                bestillingsbatchList shouldNotBeNull {
                    size shouldBe 1
                    first().status shouldBe NY
                }
                auditAfter shouldBe emptyList()
            }
        }
    })
