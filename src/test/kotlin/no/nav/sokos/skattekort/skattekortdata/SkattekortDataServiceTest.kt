package no.nav.sokos.skattekort.skattekortdata

import java.math.BigDecimal.valueOf
import java.math.RoundingMode

import kotlin.collections.first
import kotlin.collections.last
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.inspectors.forAll
import io.kotest.inspectors.forOne
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

import no.nav.sokos.skattekort.infrastructure.skatteetaten.hentskattekort.Arbeidstaker
import no.nav.sokos.skattekort.infrastructure.skatteetaten.hentskattekort.HentSkattekortResponse
import no.nav.sokos.skattekort.listener.DbListener
import no.nav.sokos.skattekort.person.Person
import no.nav.sokos.skattekort.person.PersonId
import no.nav.sokos.skattekort.person.PersonRepository
import no.nav.sokos.skattekort.skattekort.Prosentkort
import no.nav.sokos.skattekort.skattekort.ResultatForSkattekort
import no.nav.sokos.skattekort.skattekort.ResultatForSkattekort.IkkeSkattekort
import no.nav.sokos.skattekort.skattekort.ResultatForSkattekort.SkattekortopplysningerOK
import no.nav.sokos.skattekort.skattekort.Skattekort
import no.nav.sokos.skattekort.skattekort.SkattekortId
import no.nav.sokos.skattekort.skattekort.SkattekortKilde
import no.nav.sokos.skattekort.skattekort.SkattekortRepository
import no.nav.sokos.skattekort.skattekort.Tilleggsopplysning
import no.nav.sokos.skattekort.skattekort.Trekkode
import no.nav.sokos.skattekort.skattekort.Trekkode.LOENN_FRA_NAV
import no.nav.sokos.skattekort.skattekort.Trekkode.PENSJON_FRA_NAV
import no.nav.sokos.skattekort.skattekort.Trekkode.UFOERETRYGD_FRA_NAV
import no.nav.sokos.skattekort.skattekort.aBestilling
import no.nav.sokos.skattekort.skattekort.aBestillingsBatch
import no.nav.sokos.skattekort.skattekort.aForskuddstrekk
import no.nav.sokos.skattekort.skattekort.aPerson
import no.nav.sokos.skattekort.skattekort.afoedselsnummer
import no.nav.sokos.skattekort.skattekort.anAbonnement
import no.nav.sokos.skattekort.skattekort.databaseHas
import no.nav.sokos.skattekort.skattekortbestilling.Bestillingsbatch
import no.nav.sokos.skattekort.skattekortbestilling.BestillingsbatchStatus
import no.nav.sokos.skattekort.skattekorthenting.Bestilling
import no.nav.sokos.skattekort.skattekorthenting.BestillingId
import no.nav.sokos.skattekort.utils.DBTestUtils
import no.nav.sokos.skattekort.utils.TestUtils.readFile
import no.nav.sokos.skattekort.utils.TestUtils.tx
import no.nav.sokos.skattekort.utsending.Utsending
import no.nav.sokos.skattekort.utsending.UtsendingRepository

class SkattekortDataServiceTest :
    FunSpec({
        extensions(DbListener)

        test("henteBestillingsbatcher reell response") {
//        coEvery { skatteetatenClient.hentSkattekort(any()) } returns
//                aHentSkattekortResponseFromFile("src/test/resources/skatteetaten/hentSkattekort/skattekortopplysningerOK.json")

            databaseHas(
                aPerson(1L),
                afoedselsnummer(1L, "01010112345"),
                anAbonnement(1L, personId = 1L, inntektsaar = 2025),
                aBestillingsBatch(1, "BR1337", BestillingsbatchStatus.NY.value),
                aBestilling(1L, "01010112345", 2025, 1L),
            )

//        bestillingService.hentBestillingsbatcher(BestillingsbatchType.BESTILLING)

            val skattekortList: List<Skattekort> =
                tx {
                    SkattekortRepository
                        .findAllByPersonId(it, PersonId(1), 2025, adminRole = true)
                }
            skattekortList shouldBe emptyList()
        }

        test("skattekort reell response med samme identifikator og ny informasjon") {
//        coEvery { skatteetatenClient.hentSkattekort(any()) } returns
//                aHentSkattekortResponseFromFile("src/test/resources/skatteetaten/hentSkattekort/skattekortopplysningerOK_pre.json") andThen
//                aHentSkattekortResponseFromFile(
//                    "src/test/resources/skatteetaten/hentSkattekort/skattekortopplysningerOK.json",
//                )

            databaseHas(
                aPerson(1L),
                afoedselsnummer(1L, "01010112345"),
                anAbonnement(1L, personId = 1L, inntektsaar = 2025),
                aBestillingsBatch(1, "BR1337", BestillingsbatchStatus.NY.value),
                aBestillingsBatch(2, "BR1338", BestillingsbatchStatus.NY.value),
                aBestilling(1L, "01010112345", 2025, 1L),
                aBestilling(1L, "23456789012", 2025, 2L),
            )

//        bestillingService.hentBestillingsbatcher(BestillingsbatchType.BESTILLING)

            val updatedBatchesFirstRun: List<Bestillingsbatch> = tx(DBTestUtils::getAllBestillingsbatch)
            val skattekortFirstRun: List<Skattekort> =
                tx {
                    SkattekortRepository
                        .findAllByPersonId(it, PersonId(1), 2025, adminRole = true)
                }
            val bestillingsAfterFirstRun: List<Bestilling> = tx(DBTestUtils::getAllBestilling)
            val utsendingerAfterFirstRun: List<Utsending> = tx(UtsendingRepository::getAllUtsendinger)

            assertSoftly {
                updatedBatchesFirstRun shouldNotBeNull {
                    size shouldBe 2
                    forOne {
                        it.id!!.id shouldBe 1L
                        it.status shouldBe BestillingsbatchStatus.FERDIG.value
                    }
                    forOne {
                        it.id!!.id shouldBe 2L
                        it.status shouldBe BestillingsbatchStatus.FERDIG.value
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
//        coEvery { skatteetatenClient.hentSkattekort(any()) } returns response

            databaseHas(
                aPerson(1L),
                afoedselsnummer(1L, "01010112345"),
                anAbonnement(1L, personId = 1L, inntektsaar = 2025),
                aBestillingsBatch(1, "BR1337", BestillingsbatchStatus.NY.value),
                aBestilling(1L, "01010112345", 2025, 1L),
            )

//        bestillingService.hentBestillingsbatcher(BestillingsbatchType.BESTILLING)

            val skattekort: List<Skattekort> =
                tx {
                    SkattekortRepository
                        .findAllByPersonId(it, PersonId(1), 2025, adminRole = true)
                }

            assertSoftly {
                skattekort shouldNotBeNull {
                    size shouldBe 1
                    last() shouldNotBeNull {
                        identifikator shouldBe "54407"
                        resultatForSkattekort shouldBe SkattekortopplysningerOK
                        forskuddstrekkList shouldContainExactly
                            listOf(
                                aForskuddstrekk(
                                    "Frikort",
                                    UFOERETRYGD_FRA_NAV,
                                    frikortbeløp = null,
                                ),
                                aForskuddstrekk(
                                    "Frikort",
                                    Trekkode.UFOEREYTELSER_FRA_ANDRE,
                                    frikortbeløp = null,
                                ),
                                aForskuddstrekk(
                                    "Frikort",
                                    PENSJON_FRA_NAV,
                                    frikortbeløp = null,
                                ),
                                aForskuddstrekk(
                                    "Frikort",
                                    Trekkode.PENSJON,
                                    frikortbeløp = null,
                                ),
                            )
                    }
                }
            }
        }

        test("henter skattekort med alle tilleggsopplysninger") {
//        coEvery { skatteetatenClient.hentSkattekort(any()) } returns
//                aHentSkattekortResponse(
//                    anArbeidstaker(
//                        resultat = SkattekortopplysningerOK,
//                        fnr = "01010100001",
//                        inntektsaar = 2025,
//                        skattekort =
//                            aSkattekort(
//                                utstedtDato = "2025-11-01",
//                                identifikator = 10001,
//                                forskuddstrekk =
//                                    listOf(
//                                        Forskuddstrekk(
//                                            trekkode = UFOERETRYGD_FRA_NAV.value,
//                                            trekkprosent = Trekkprosent(valueOf(43)),
//                                        ),
//                                    ),
//                            ),
//                        tilleggsopplysninger =
//                            listOf(
//                                Tilleggsopplysning.fromValue("oppholdPaaSvalbard"),
//                                Tilleggsopplysning.fromValue("kildeskattPaaPensjon"),
//                                Tilleggsopplysning.fromValue("oppholdITiltakssone"),
//                            ),
//                    ),
//                )

            databaseHas(
                aPerson(1L),
                afoedselsnummer(personId = 1L, fnr = "01010100001"),
                anAbonnement(1L, personId = 1L, inntektsaar = 2025),
                aBestillingsBatch(1, "ref1", BestillingsbatchStatus.NY.value),
                aBestilling(1L, "01010100001", 2025, 1L),
            )

//        bestillingService.hentBestillingsbatcher(BestillingsbatchType.BESTILLING)

            val skattekort: List<Skattekort> =
                tx {
                    SkattekortRepository
                        .findAllByPersonId(it, PersonId(1), 2025, adminRole = false)
                }
            val bestillingsAfter: List<Bestilling> = tx(DBTestUtils::getAllBestilling)
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
                                Tilleggsopplysning.Companion
                                    .fromValue("oppholdPaaSvalbard"),
                                Tilleggsopplysning.Companion
                                    .fromValue("kildeskattPaaPensjon"),
                                Tilleggsopplysning.Companion
                                    .fromValue("oppholdITiltakssone"),
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
                                    aForskuddstrekk(
                                        "Prosentkort",
                                        LOENN_FRA_NAV,
                                        15.70,
                                    ),
                                    aForskuddstrekk(
                                        "Prosentkort",
                                        UFOERETRYGD_FRA_NAV,
                                        15.70,
                                    ),
                                    aForskuddstrekk(
                                        "Prosentkort",
                                        PENSJON_FRA_NAV,
                                        13.10,
                                    ),
                                )
                        }
                        tilleggsopplysningList shouldNotBeNull {
                            shouldContainExactly(
                                Tilleggsopplysning.Companion
                                    .fromValue("oppholdPaaSvalbard"),
                                Tilleggsopplysning.Companion
                                    .fromValue("kildeskattPaaPensjon"),
                                Tilleggsopplysning.Companion
                                    .fromValue("oppholdITiltakssone"),
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

        test("ugyldigFoedselsEllerDnummer") {
//            coEvery { skatteetatenClient.hentSkattekort(any()) } returns
//                    aHentSkattekortResponse(
//                        anArbeidstaker(
//                            resultat = ResultatForSkattekort.UgyldigFoedselsEllerDnummer,
//                            fnr = "01010100001",
//                            inntektsaar = 2025,
//                        ),
//                    ) andThen
//                    aHentSkattekortResponse(
//                        anArbeidstaker(
//                            resultat = IkkeSkattekort,
//                            fnr = "02020200002",
//                            inntektsaar = 2025,
//                        ),
//                    )

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

            //  bestillingService.hentBestillingsbatcher(BestillingsbatchType.BESTILLING)

            val updatedBatches: List<Bestillingsbatch> = tx(DBTestUtils::getAllBestillingsbatch)
            val bestillingsAfter: List<Bestilling> = tx(DBTestUtils::getAllBestilling)
            val skattekortPerson1: List<Skattekort> =
                tx {
                    SkattekortRepository
                        .findAllByPersonId(it, PersonId(1L), 2025, adminRole = false)
                }
            val skattekortPerson2: List<Skattekort> =
                tx {
                    SkattekortRepository
                        .findAllByPersonId(it, PersonId(2L), 2025, adminRole = false)
                }
            val person1: Person = tx { PersonRepository.findPersonById(it, PersonId(1L)) }
            val person2: Person = tx { PersonRepository.findPersonById(it, PersonId(2L)) }

            assertSoftly {
                updatedBatches shouldNotBeNull {
                    forAll { it.status shouldBe BestillingsbatchStatus.FERDIG.value }
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
//            coEvery { skatteetatenClient.hentSkattekort(any()) } returns
//                    HentSkattekortResponse(
//                        status = "FORESPOERSEL_OK",
//                        arbeidsgiver =
//                            listOf(
//                                Arbeidsgiver(
//                                    arbeidsgiveridentifikator =
//                                        Arbeidsgiveridentifikator(
//                                            organisasjonsnummer = "666",
//                                        ),
//                                    arbeidstaker =
//                                        listOf(
//                                            anArbeidstaker(
//                                                resultat = ResultatForSkattekort.UgyldigOrganisasjonsnummer,
//                                                fnr = "01010100001",
//                                                inntektsaar = 2025,
//                                            ),
//                                        ),
//                                ),
//                            ),
//                    )

            databaseHas(
                aPerson(1L),
                afoedselsnummer(1L, "01010100001"),
                aBestillingsBatch(id = 1L, ref = "ref1", status = "NY"),
                aBestilling(personId = 1L, fnr = "01010100001", inntektsaar = 2025, batchId = 1L),
            )

            shouldThrow<no.nav.sokos.skattekort.skattekort.UgyldigOrganisasjonsnummerException> {
                //   bestillingService.hentBestillingsbatcher(BestillingsbatchType.OPPDATERING)
            }

            val updatedBatches: List<Bestillingsbatch> = tx(DBTestUtils::getAllBestillingsbatch)
            val bestillingsAfter: List<Bestilling> = tx(DBTestUtils::getAllBestilling)
            val skattekort: List<Skattekort> =
                tx {
                    SkattekortRepository
                        .findAllByPersonId(it, PersonId(1L), 2025, adminRole = false)
                }
            val person1: Person = tx { PersonRepository.findPersonById(it, PersonId(1L)) }

            updatedBatches shouldNotBeNull {
                size shouldBe 1
                forOne { it.status shouldBe BestillingsbatchStatus.FEILET.value }
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
//            coEvery { skatteetatenClient.hentSkattekort(any()) } returns
//                    aHentSkattekortResponse(
//                        anArbeidstaker(
//                            resultat = IkkeSkattekort,
//                            fnr = "01010100001",
//                            inntektsaar = 2025,
//                            tilleggsopplysninger =
//                                listOf(
//                                    Tilleggsopplysning.Companion
//                                        .fromValue("oppholdPaaSvalbard"),
//                                ),
//                        ),
//                    )
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

            //  bestillingService.hentBestillingsbatcher(BestillingsbatchType.BESTILLING)

            val updatedBatches: List<Bestillingsbatch> = tx(DBTestUtils::getAllBestillingsbatch)
            val skattekort: List<Skattekort> =
                tx {
                    SkattekortRepository
                        .findAllByPersonId(it, PersonId(1), 2025, adminRole = false)
                }
            val bestillingsAfter: List<Bestilling> = tx(DBTestUtils::getAllBestilling)

            assertSoftly {
                updatedBatches.count { it.status == BestillingsbatchStatus.FERDIG.value } shouldBe 2

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
                        tilleggsopplysningList shouldContainExactly
                            listOf(
                                Tilleggsopplysning.Companion
                                    .fromValue("oppholdPaaSvalbard"),
                            )
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
                                    aForskuddstrekk(
                                        "Prosentkort",
                                        LOENN_FRA_NAV,
                                        15.70,
                                    ),
                                    aForskuddstrekk(
                                        "Prosentkort",
                                        UFOERETRYGD_FRA_NAV,
                                        15.70,
                                    ),
                                    aForskuddstrekk(
                                        "Prosentkort",
                                        PENSJON_FRA_NAV,
                                        13.10,
                                    ),
                                )
                        }
                        tilleggsopplysningList shouldContainExactly
                            listOf(
                                Tilleggsopplysning.Companion
                                    .fromValue("oppholdPaaSvalbard"),
                            )
                    }
                }
            }
        }
        test("skattekortOpplysningerOk med oppholdPaaSvalbard") {
//            coEvery { skatteetatenClient.hentSkattekort(any()) } returns
//                    aHentSkattekortResponse(
//                        anArbeidstaker(
//                            resultat = SkattekortopplysningerOK,
//                            fnr = "01010100001",
//                            inntektsaar = 2025,
//                            tilleggsopplysninger =
//                                listOf(
//                                    Tilleggsopplysning.Companion
//                                        .fromValue("oppholdPaaSvalbard"),
//                                ),
//                            skattekort =
//                                aSkattekort(
//                                    utstedtDato = "2025-11-01",
//                                    identifikator = 10001,
//                                    forskuddstrekk =
//                                        listOf(
//                                            aSkdForskuddstrekk(
//                                                Trekkode.LOENN_FRA_HOVEDARBEIDSGIVER,
//                                                tabellNummer = "1337",
//                                                trekkprosent = 43.21,
//                                            ),
//                                            aSkdForskuddstrekk(
//                                                LOENN_FRA_NAV,
//                                                66.60,
//                                            ),
//                                            aSkdForskuddstrekk(
//                                                PENSJON_FRA_NAV,
//                                                6.66,
//                                            ),
//                                            aSkdForskuddstrekk(
//                                                UFOERETRYGD_FRA_NAV,
//                                                12.34,
//                                            ),
//                                        ),
//                                ),
//                        ),
//                    )
            databaseHas(
                aPerson(1L),
                afoedselsnummer(personId = 1L, fnr = "01010100001"),
                aBestillingsBatch(id = 1L, ref = "ref1", status = "NY"),
                aBestilling(personId = 1L, fnr = "01010100001", inntektsaar = 2025, batchId = 1L),
            )

            //    bestillingService.hentBestillingsbatcher(BestillingsbatchType.BESTILLING)

            val updatedBatches: List<Bestillingsbatch> = tx(DBTestUtils::getAllBestillingsbatch)
            val skattekort: List<Skattekort> =
                tx {
                    SkattekortRepository
                        .findAllByPersonId(it, PersonId(1), 2025, adminRole = true)
                }
            val bestillingsAfter: List<Bestilling> = tx(DBTestUtils::getAllBestilling)

            assertSoftly {
                updatedBatches.count { it.status == BestillingsbatchStatus.FERDIG.value } shouldBe 1

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
                            utstedtDato shouldBe LocalDate.parse("2025-11-01")
                            withClue("Should contain the received forskuddstrekk unchanged") {
                                forskuddstrekkList shouldContainAll
                                    listOf(
                                        aForskuddstrekk(
                                            "Tabellkort",
                                            Trekkode.LOENN_FRA_HOVEDARBEIDSGIVER,
                                            tabellNummer = "1337",
                                            prosentSats = 43.21,
                                            antMndForTrekk = 12.0,
                                        ),
                                        aForskuddstrekk(
                                            "Prosentkort",
                                            LOENN_FRA_NAV,
                                            66.60,
                                        ),
                                        aForskuddstrekk(
                                            "Prosentkort",
                                            PENSJON_FRA_NAV,
                                            6.66,
                                        ),
                                        aForskuddstrekk(
                                            "Prosentkort",
                                            UFOERETRYGD_FRA_NAV,
                                            12.34,
                                        ),
                                    )
                            }
                            tilleggsopplysningList shouldContainExactly
                                listOf(
                                    Tilleggsopplysning.Companion
                                        .fromValue("oppholdPaaSvalbard"),
                                )
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
                                        aForskuddstrekk(
                                            "Prosentkort",
                                            LOENN_FRA_NAV,
                                            15.70,
                                        ),
                                        aForskuddstrekk(
                                            "Prosentkort",
                                            UFOERETRYGD_FRA_NAV,
                                            15.70,
                                        ),
                                        aForskuddstrekk(
                                            "Prosentkort",
                                            PENSJON_FRA_NAV,
                                            13.10,
                                        ),
                                    )
                            }
                            it.tilleggsopplysningList shouldContainExactly
                                listOf(
                                    Tilleggsopplysning.Companion
                                        .fromValue("oppholdPaaSvalbard"),
                                )
                        }
                    }
                }
            }
        }

        test("ikkeTrekkplikt") {
//            coEvery { skatteetatenClient.hentSkattekort(any()) } returns
//                    aHentSkattekortResponse(
//                        anArbeidstaker(
//                            resultat = ResultatForSkattekort.IkkeTrekkplikt,
//                            fnr = "01010100001",
//                            inntektsaar = 2025,
//                        ),
//                    )
            databaseHas(
                aPerson(1L),
                afoedselsnummer(personId = 1L, fnr = "01010100001"),
                aBestillingsBatch(id = 1L, ref = "ref1", status = "NY"),
                aBestilling(personId = 1L, fnr = "01010100001", inntektsaar = 2025, batchId = 1L),
            )

            //   bestillingService.hentBestillingsbatcher(BestillingsbatchType.BESTILLING)

            val updatedBatches: List<Bestillingsbatch> = tx(DBTestUtils::getAllBestillingsbatch)
            val skattekort: List<Skattekort> =
                tx {
                    SkattekortRepository
                        .findAllByPersonId(it, PersonId(1), 2025, adminRole = false)
                }
            val bestillingsAfter: List<Bestilling> = tx(DBTestUtils::getAllBestilling)

            assertSoftly {
                updatedBatches.count { it.status == BestillingsbatchStatus.FERDIG.value } shouldBe 1

                bestillingsAfter shouldBe emptyList()

                skattekort shouldNotBeNull {
                    size shouldBe 2
                    last() shouldNotBeNull {
                        kilde shouldBe SkattekortKilde.SKATTEETATEN.value
                        resultatForSkattekort shouldBe ResultatForSkattekort.IkkeTrekkplikt
                        utstedtDato shouldBe null
                        identifikator shouldBe null
                        forskuddstrekkList shouldBe emptyList()
                    }
                    first() shouldNotBeNull {
                        kilde shouldBe SkattekortKilde.SYNTETISERT.value
                        generertFra shouldBe last().id
                        resultatForSkattekort shouldBe ResultatForSkattekort.IkkeTrekkplikt
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
    })
