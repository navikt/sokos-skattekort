package no.nav.sokos.skattekort.skattekortdata

import java.math.BigDecimal

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

import no.nav.sokos.skattekort.forespoersel.Forsystem
import no.nav.sokos.skattekort.listener.DbListener
import no.nav.sokos.skattekort.person.AuditRepository
import no.nav.sokos.skattekort.person.AuditTag
import no.nav.sokos.skattekort.person.PersonId
import no.nav.sokos.skattekort.skattekort.Frikort
import no.nav.sokos.skattekort.skattekort.Prosentkort
import no.nav.sokos.skattekort.skattekort.ResultatForSkattekort
import no.nav.sokos.skattekort.skattekort.ResultatForSkattekort.IkkeSkattekort
import no.nav.sokos.skattekort.skattekort.ResultatForSkattekort.SkattekortopplysningerOK
import no.nav.sokos.skattekort.skattekort.Skattekort
import no.nav.sokos.skattekort.skattekort.SkattekortKilde
import no.nav.sokos.skattekort.skattekort.SkattekortRepository
import no.nav.sokos.skattekort.skattekort.Tabellkort
import no.nav.sokos.skattekort.skattekort.Tilleggsopplysning
import no.nav.sokos.skattekort.skattekort.Trekkode
import no.nav.sokos.skattekort.skattekort.aPerson
import no.nav.sokos.skattekort.skattekort.afoedselsnummer
import no.nav.sokos.skattekort.skattekort.anAbonnement
import no.nav.sokos.skattekort.skattekort.databaseHas
import no.nav.sokos.skattekort.skattekortbestilling.BestillingsbatchType
import no.nav.sokos.skattekort.utils.TestUtils.readFile
import no.nav.sokos.skattekort.utils.TestUtils.tx
import no.nav.sokos.skattekort.utsending.UtsendingRepository

class SkattekortDataServiceTest :
    FunSpec({
        extensions(DbListener)

        val skattekortDataService: SkattekortDataService by lazy {
            SkattekortDataService(DbListener.dataSource)
        }

        val fnr = "01010112345"

        test("processSkattekortData should do nothing when no unprocessed skattekort data exists") {
            databaseHas(
                aPerson(1L),
                afoedselsnummer(1L, fnr),
                anAbonnement(1L, personId = 1L, inntektsaar = 2025),
            )

            skattekortDataService.processSkattekortData()

            val skattekort: List<Skattekort> =
                tx {
                    SkattekortRepository.findAllByPersonId(it, listOf(PersonId(1)), listOf(2025), adminRole = true)
                }
            skattekort.shouldBeEmpty()
        }

        test("processSkattekortData should persist skattekort and create utsending for abonnement") {

            databaseHas(
                aPerson(1L),
                afoedselsnummer(1L, fnr),
                anAbonnement(1L, personId = 1L, inntektsaar = 2025),
            )

            val skattekortJson = readFile("/skatteetaten/skattekortData/skattekortopplysningerOK.json")
            tx { SkattekortDataRepository.insert(it, skattekortJson, 2025, fnr, BestillingsbatchType.BESTILLING) }

            skattekortDataService.processSkattekortData()

            val skattekortList = tx { SkattekortRepository.findAllByPersonId(it, listOf(PersonId(1)), listOf(2025), adminRole = true) }
            val utsendingList = tx(UtsendingRepository::getAllUtsendinger)
            val skattekortDataList = tx(SkattekortDataRepository::getUnprocessedSkattekortData)

            assertSoftly {
                skattekortDataList shouldBe emptyList()
                skattekortList shouldNotBeNull {
                    size shouldBe 1
                    first() shouldNotBeNull {
                        generertFra shouldBe null
                        identifikator shouldBe "54407"
                        personId shouldBe PersonId(1L)
                        inntektsaar shouldBe 2025
                        kilde shouldBe SkattekortKilde.SKATTEETATEN.value
                        resultatForSkattekort shouldBe SkattekortopplysningerOK
                        forskuddstrekkList.shouldContainExactly(
                            Tabellkort(Trekkode.LOENN_FRA_HOVEDARBEIDSGIVER, "8140", BigDecimal("43.00"), BigDecimal("10.5")),
                            Prosentkort(Trekkode.LOENN_FRA_BIARBEIDSGIVER, BigDecimal("43.00")),
                            Prosentkort(Trekkode.LOENN_FRA_NAV, BigDecimal("43.00")),
                            Prosentkort(Trekkode.UFOERETRYGD_FRA_NAV, BigDecimal("43.00")),
                            Prosentkort(Trekkode.UFOEREYTELSER_FRA_ANDRE, BigDecimal("43.00")),
                        )

                        tilleggsopplysningList shouldBe emptyList()
                    }
                }

                utsendingList shouldNotBeNull {
                    size shouldBe 1
                    first() shouldNotBeNull {
                        this.fnr.value shouldBe fnr
                        inntektsaar shouldBe 2025
                        forsystem shouldBe Forsystem.OPPDRAGSSYSTEMET
                        failMessage shouldBe null
                        failCount shouldBe 0
                    }
                }
            }
        }

        test("processSkattekortData should handle tomt frikort and keep frikortbeløp as null") {
            databaseHas(
                aPerson(1L),
                afoedselsnummer(1L, fnr),
                anAbonnement(1L, personId = 1L, inntektsaar = 2025),
            )

            val skattekortJson = readFile("/skatteetaten/skattekortData/skattekortopplysningerOK_med_tomt_frikort.json")
            tx { SkattekortDataRepository.insert(it, skattekortJson, 2025, fnr, BestillingsbatchType.BESTILLING) }

            skattekortDataService.processSkattekortData()

            val skattekortList = tx { SkattekortRepository.findAllByPersonId(it, listOf(PersonId(1)), listOf(2025), adminRole = true) }
            val utsendingList = tx(UtsendingRepository::getAllUtsendinger)
            val skattekortDataList = tx(SkattekortDataRepository::getUnprocessedSkattekortData)

            assertSoftly {
                skattekortDataList shouldBe emptyList()
                skattekortList shouldNotBeNull {
                    size shouldBe 1
                    first() shouldNotBeNull {
                        generertFra shouldBe null
                        identifikator shouldBe "54407"
                        personId shouldBe PersonId(1L)
                        inntektsaar shouldBe 2025
                        kilde shouldBe SkattekortKilde.SKATTEETATEN.value
                        resultatForSkattekort shouldBe SkattekortopplysningerOK
                        forskuddstrekkList.shouldContainExactly(
                            Frikort(Trekkode.UFOERETRYGD_FRA_NAV, null),
                            Frikort(Trekkode.UFOEREYTELSER_FRA_ANDRE, null),
                            Frikort(Trekkode.PENSJON_FRA_NAV, null),
                            Frikort(Trekkode.PENSJON, null),
                        )

                        tilleggsopplysningList.shouldContainExactly(
                            Tilleggsopplysning.KILDESKATT_PAA_PENSJON,
                        )
                    }
                }

                utsendingList shouldNotBeNull {
                    size shouldBe 1
                    first() shouldNotBeNull {
                        this.fnr.value shouldBe fnr
                        inntektsaar shouldBe 2025
                        forsystem shouldBe Forsystem.OPPDRAGSSYSTEMET
                        failMessage shouldBe null
                        failCount shouldBe 0
                    }
                }
            }
        }

        test("processSkattekortData should persist tilleggsopplysninger and synthesize skattekort when oppholdPaaSvalbard is present") {
            databaseHas(
                aPerson(1L),
                afoedselsnummer(personId = 1L, fnr = fnr),
                anAbonnement(1L, personId = 1L, inntektsaar = 2025),
            )

            val skattekortJson = readFile("/skatteetaten/skattekortData/skattekortopplysningerOK_med_oppholdPaaSvalbard.json")
            tx { SkattekortDataRepository.insert(it, skattekortJson, 2025, fnr, BestillingsbatchType.BESTILLING) }

            skattekortDataService.processSkattekortData()

            val skattekortList = tx { SkattekortRepository.findAllByPersonId(it, listOf(PersonId(1)), listOf(2025), adminRole = true) }
            val utsendingList = tx(UtsendingRepository::getAllUtsendinger)
            val auditList = tx { AuditRepository.getAuditByPersonId(it, PersonId(1)) }
            val skattekortDataList = tx(SkattekortDataRepository::getUnprocessedSkattekortData)

            assertSoftly {
                skattekortDataList shouldBe emptyList()
                skattekortList shouldNotBeNull {
                    size shouldBe 2

                    val original = first { it.kilde == SkattekortKilde.SKATTEETATEN.value }
                    val syntetisert = first { it.kilde == SkattekortKilde.SYNTETISERT.value }

                    original shouldNotBeNull {
                        generertFra shouldBe null
                        identifikator shouldBe "54407"
                        personId shouldBe PersonId(1L)
                        inntektsaar shouldBe 2025
                        kilde shouldBe SkattekortKilde.SKATTEETATEN.value
                        resultatForSkattekort shouldBe SkattekortopplysningerOK
                        forskuddstrekkList.shouldContainExactly(
                            Tabellkort(Trekkode.LOENN_FRA_HOVEDARBEIDSGIVER, "8140", BigDecimal("43.00"), BigDecimal("10.5")),
                            Prosentkort(Trekkode.LOENN_FRA_BIARBEIDSGIVER, BigDecimal("43.00")),
                            Prosentkort(Trekkode.LOENN_FRA_NAV, BigDecimal("43.00")),
                            Prosentkort(Trekkode.UFOERETRYGD_FRA_NAV, BigDecimal("43.00")),
                            Prosentkort(Trekkode.UFOEREYTELSER_FRA_ANDRE, BigDecimal("43.00")),
                        )
                        tilleggsopplysningList.shouldContainExactly(Tilleggsopplysning.OPPHOLD_PAA_SVALBARD)
                    }

                    syntetisert shouldNotBeNull {
                        generertFra shouldBe original.id
                        identifikator shouldBe null
                        personId shouldBe PersonId(1L)
                        inntektsaar shouldBe 2025
                        kilde shouldBe SkattekortKilde.SYNTETISERT.value
                        resultatForSkattekort shouldBe SkattekortopplysningerOK
                        forskuddstrekkList.shouldContainExactly(
                            Prosentkort(Trekkode.LOENN_FRA_NAV, BigDecimal("15.70"), null),
                            Prosentkort(Trekkode.PENSJON_FRA_NAV, BigDecimal("13.10"), null),
                            Prosentkort(Trekkode.UFOERETRYGD_FRA_NAV, BigDecimal("15.70"), null),
                        )
                    }
                }

                utsendingList shouldNotBeNull {
                    size shouldBe 1
                    first() shouldNotBeNull {
                        this.fnr.value shouldBe fnr
                        inntektsaar shouldBe 2025
                        forsystem shouldBe Forsystem.OPPDRAGSSYSTEMET
                        failMessage shouldBe null
                        failCount shouldBe 0
                    }
                }

                auditList shouldNotBeNull {
                    size shouldBe 1
                    first() shouldNotBeNull {
                        personId shouldBe PersonId(1L)
                        brukerId shouldBe "system"
                        tag shouldBe AuditTag.SYNTETISERT_SKATTEKORT
                        informasjon shouldBe "Prosentkort med default skattesatser for Svalbard syntetisert pga mottatt tilleggsinformasjon oppholdPaaSvalbard"
                    }
                }
            }
        }

        test("processSkattekortData should persist IkkeTrekkplikt and generate synthetic frikort") {
            databaseHas(
                aPerson(1L),
                afoedselsnummer(personId = 1L, fnr = fnr),
                anAbonnement(1L, personId = 1L, inntektsaar = 2025),
            )

            val skattekortJson =
                """
                {
                  "arbeidstakeridentifikator": "$fnr",
                  "resultatForSkattekort": "ikkeTrekkplikt",
                  "inntektsaar": 2025
                }
                """.trimIndent()

            tx { SkattekortDataRepository.insert(it, skattekortJson, 2025, fnr, BestillingsbatchType.BESTILLING) }

            skattekortDataService.processSkattekortData()

            val skattekortList = tx { SkattekortRepository.findAllByPersonId(it, listOf(PersonId(1)), listOf(2025), adminRole = true) }
            val utsendingList = tx(UtsendingRepository::getAllUtsendinger)
            val auditList = tx { AuditRepository.getAuditByPersonId(it, PersonId(1)) }
            val skattekortDataList = tx(SkattekortDataRepository::getUnprocessedSkattekortData)

            assertSoftly {
                skattekortDataList shouldBe emptyList()
                skattekortList shouldNotBeNull {
                    size shouldBe 2

                    val original = first { it.kilde == SkattekortKilde.SKATTEETATEN.value }
                    val syntetisert = first { it.kilde == SkattekortKilde.SYNTETISERT.value }

                    original shouldNotBeNull {
                        generertFra shouldBe null
                        identifikator shouldBe null
                        personId shouldBe PersonId(1L)
                        inntektsaar shouldBe 2025
                        kilde shouldBe SkattekortKilde.SKATTEETATEN.value
                        resultatForSkattekort shouldBe ResultatForSkattekort.IkkeTrekkplikt
                        forskuddstrekkList shouldBe emptyList()
                        tilleggsopplysningList shouldBe emptyList()
                    }

                    syntetisert shouldNotBeNull {
                        generertFra shouldBe original.id
                        identifikator shouldBe null
                        personId shouldBe PersonId(1L)
                        inntektsaar shouldBe 2025
                        kilde shouldBe SkattekortKilde.SYNTETISERT.value
                        resultatForSkattekort shouldBe ResultatForSkattekort.IkkeTrekkplikt
                        forskuddstrekkList.shouldContainExactly(
                            Frikort(Trekkode.LOENN_FRA_NAV, null),
                            Frikort(Trekkode.PENSJON_FRA_NAV, null),
                            Frikort(Trekkode.UFOERETRYGD_FRA_NAV, null),
                        )
                    }
                }

                utsendingList shouldNotBeNull {
                    size shouldBe 1
                    first() shouldNotBeNull {
                        this.fnr.value shouldBe fnr
                        inntektsaar shouldBe 2025
                        forsystem shouldBe Forsystem.OPPDRAGSSYSTEMET
                        failMessage shouldBe null
                        failCount shouldBe 0
                    }
                }

                auditList shouldNotBeNull {
                    size shouldBe 1
                    first() shouldNotBeNull {
                        personId shouldBe PersonId(1L)
                        brukerId shouldBe "system"
                        tag shouldBe AuditTag.SYNTETISERT_SKATTEKORT
                        informasjon shouldBe "Frikort uten beløpsgrense syntetisert fordi brukeren ikke er trekkpliktig"
                    }
                }
            }
        }

        test("processSkattekortData should not create duplicate utsendinger for same abonnement when run twice for same data") {
            databaseHas(
                aPerson(1L),
                afoedselsnummer(1L, fnr),
                anAbonnement(1L, personId = 1L, inntektsaar = 2025),
            )

            val skattekortJson =
                """
                {
                  "arbeidstakeridentifikator": "$fnr",
                  "resultatForSkattekort": "skattekortopplysningerOK",
                  "inntektsaar": 2025,
                  "skattekort": {
                    "utstedtDato": "2025-11-01",
                    "skattekortidentifikator": 10001,
                    "forskuddstrekk": []
                  }
                }
                """.trimIndent()
            tx { SkattekortDataRepository.insert(it, skattekortJson, 2025, fnr, BestillingsbatchType.BESTILLING) }

            skattekortDataService.processSkattekortData()
            skattekortDataService.processSkattekortData()

            val utsendinger = tx(UtsendingRepository::getAllUtsendinger)
            utsendinger shouldHaveSize 1
        }

        test("processSkattekortData should not create utsendinger for person not exists in database") {
            databaseHas(
                aPerson(1L),
                afoedselsnummer(1L, fnr),
                anAbonnement(1L, personId = 1L, inntektsaar = 2025),
            )

            val skattekortJson =
                """
                {
                  "arbeidstakeridentifikator": "01010112345",
                  "resultatForSkattekort": "skattekortopplysningerOK",
                  "inntektsaar": 2025,
                  "skattekort": {
                    "utstedtDato": "2025-11-01",
                    "skattekortidentifikator": 10001,
                    "forskuddstrekk": []
                  }
                }
                """.trimIndent()
            tx { SkattekortDataRepository.insert(it, skattekortJson, 2025, fnr, BestillingsbatchType.BESTILLING) }
        }

        test("processSkattekortData should persist all tilleggsopplysninger and synthesize skattekort") {
            databaseHas(
                aPerson(1L),
                afoedselsnummer(personId = 1L, fnr = fnr),
                anAbonnement(1L, personId = 1L, inntektsaar = 2025),
            )

            val skattekortJson = readFile("/skatteetaten/skattekortData/skattekortopplysningerOK_med_alle_tilleggsopplysninger.json")
            tx { SkattekortDataRepository.insert(it, skattekortJson, 2025, fnr, BestillingsbatchType.BESTILLING) }

            skattekortDataService.processSkattekortData()

            val skattekortList = tx { SkattekortRepository.findAllByPersonId(it, listOf(PersonId(1)), listOf(2025), adminRole = true) }
            val utsendingList = tx(UtsendingRepository::getAllUtsendinger)
            val auditList = tx { AuditRepository.getAuditByPersonId(it, PersonId(1)) }
            val skattekortDataList = tx(SkattekortDataRepository::getUnprocessedSkattekortData)

            assertSoftly {
                skattekortDataList shouldBe emptyList()
                skattekortList shouldNotBeNull {
                    size shouldBe 2

                    val original = first { it.kilde == SkattekortKilde.SKATTEETATEN.value }
                    val syntetisert = first { it.kilde == SkattekortKilde.SYNTETISERT.value }

                    original shouldNotBeNull {
                        generertFra shouldBe null
                        identifikator shouldBe "54407"
                        personId shouldBe PersonId(1L)
                        inntektsaar shouldBe 2025
                        kilde shouldBe SkattekortKilde.SKATTEETATEN.value
                        resultatForSkattekort shouldBe SkattekortopplysningerOK
                        forskuddstrekkList.shouldContainExactly(
                            Tabellkort(Trekkode.LOENN_FRA_HOVEDARBEIDSGIVER, "8140", BigDecimal("43.00"), BigDecimal("10.5")),
                            Prosentkort(Trekkode.LOENN_FRA_BIARBEIDSGIVER, BigDecimal("43.00")),
                            Prosentkort(Trekkode.LOENN_FRA_NAV, BigDecimal("43.00")),
                            Prosentkort(Trekkode.UFOERETRYGD_FRA_NAV, BigDecimal("43.00")),
                            Prosentkort(Trekkode.UFOEREYTELSER_FRA_ANDRE, BigDecimal("43.00")),
                        )
                        tilleggsopplysningList.shouldContainExactly(
                            Tilleggsopplysning.OPPHOLD_PAA_SVALBARD,
                            Tilleggsopplysning.KILDESKATT_PAA_PENSJON,
                            Tilleggsopplysning.OPPHOLD_I_TILTAKSSONE,
                        )
                    }

                    syntetisert shouldNotBeNull {
                        generertFra shouldBe original.id
                        identifikator shouldBe null
                        personId shouldBe PersonId(1L)
                        inntektsaar shouldBe 2025
                        kilde shouldBe SkattekortKilde.SYNTETISERT.value
                        resultatForSkattekort shouldBe SkattekortopplysningerOK
                        forskuddstrekkList.shouldContainExactly(
                            Prosentkort(Trekkode.LOENN_FRA_NAV, BigDecimal("15.70"), null),
                            Prosentkort(Trekkode.PENSJON_FRA_NAV, BigDecimal("13.10"), null),
                            Prosentkort(Trekkode.UFOERETRYGD_FRA_NAV, BigDecimal("15.70"), null),
                        )
                        tilleggsopplysningList.shouldContainExactly(
                            Tilleggsopplysning.OPPHOLD_PAA_SVALBARD,
                            Tilleggsopplysning.KILDESKATT_PAA_PENSJON,
                            Tilleggsopplysning.OPPHOLD_I_TILTAKSSONE,
                        )
                    }
                }

                utsendingList shouldNotBeNull {
                    size shouldBe 1
                    first() shouldNotBeNull {
                        this.fnr.value shouldBe fnr
                        inntektsaar shouldBe 2025
                        forsystem shouldBe Forsystem.OPPDRAGSSYSTEMET
                        failMessage shouldBe null
                        failCount shouldBe 0
                    }
                }

                auditList shouldNotBeNull {
                    size shouldBe 1
                    first() shouldNotBeNull {
                        personId shouldBe PersonId(1L)
                        brukerId shouldBe "system"
                        tag shouldBe AuditTag.SYNTETISERT_SKATTEKORT
                        informasjon shouldBe "Prosentkort med default skattesatser for Svalbard syntetisert pga mottatt tilleggsinformasjon oppholdPaaSvalbard"
                    }
                }
            }
        }

        test("processSkattekortData should persist ugyldigFoedselsEllerDnummer skattekort") {
            databaseHas(
                aPerson(1L),
                afoedselsnummer(1L, fnr),
                anAbonnement(1L, personId = 1L, inntektsaar = 2025),
            )

            val skattekortJson =
                """                
                {
                  "arbeidstakeridentifikator": "$fnr",
                  "resultatForSkattekort": "ugyldigFoedselsEllerDnummer",
                  "inntektsaar": 2025
                }                
                """.trimIndent()
            tx { SkattekortDataRepository.insert(it, skattekortJson, 2025, fnr, BestillingsbatchType.BESTILLING) }

            skattekortDataService.processSkattekortData()

            val skattekortList = tx { SkattekortRepository.findAllByPersonId(it, listOf(PersonId(1)), listOf(2025), adminRole = true) }
            val utsendingList = tx(UtsendingRepository::getAllUtsendinger)
            val skattekortDataList = tx(SkattekortDataRepository::getUnprocessedSkattekortData)

            assertSoftly {
                skattekortDataList shouldBe emptyList()
                skattekortList shouldNotBeNull {
                    size shouldBe 1
                    first() shouldNotBeNull {
                        identifikator shouldBe null
                        personId shouldBe PersonId(1L)
                        inntektsaar shouldBe 2025
                        kilde shouldBe SkattekortKilde.SKATTEETATEN.value
                        resultatForSkattekort shouldBe ResultatForSkattekort.UgyldigFoedselsEllerDnummer
                        forskuddstrekkList shouldBe emptyList()
                        tilleggsopplysningList shouldBe emptyList()
                    }
                }

                utsendingList shouldNotBeNull {
                    size shouldBe 1
                    first() shouldNotBeNull {
                        this.fnr.value shouldBe fnr
                        inntektsaar shouldBe 2025
                        forsystem shouldBe Forsystem.OPPDRAGSSYSTEMET
                        failMessage shouldBe null
                        failCount shouldBe 0
                    }
                }
            }
        }

        test("processSkattekortData should persist kkeSkattekort with tilleggsopplysninger oppholdPaaSvalbard") {
            databaseHas(
                aPerson(1L),
                afoedselsnummer(1L, fnr),
                anAbonnement(1L, personId = 1L, inntektsaar = 2025),
            )

            val skattekortJson =
                """                
                {
                  "arbeidstakeridentifikator": "$fnr",
                  "resultatForSkattekort": "ikkeSkattekort",
                  "tilleggsopplysning": [
                        "oppholdPaaSvalbard"
                    ],
                  "inntektsaar": 2025
                }                
                """.trimIndent()
            tx { SkattekortDataRepository.insert(it, skattekortJson, 2025, fnr, BestillingsbatchType.BESTILLING) }

            skattekortDataService.processSkattekortData()

            val skattekortList = tx { SkattekortRepository.findAllByPersonId(it, listOf(PersonId(1)), listOf(2025), adminRole = true) }
            val utsendingList = tx(UtsendingRepository::getAllUtsendinger)
            val auditList = tx { AuditRepository.getAuditByPersonId(it, PersonId(1)) }
            val skattekortDataList = tx(SkattekortDataRepository::getUnprocessedSkattekortData)

            assertSoftly {
                skattekortDataList shouldBe emptyList()
                skattekortList shouldNotBeNull {
                    size shouldBe 2

                    val original = first { it.kilde == SkattekortKilde.SKATTEETATEN.value }
                    val syntetisert = first { it.kilde == SkattekortKilde.SYNTETISERT.value }

                    original shouldNotBeNull {
                        generertFra shouldBe null
                        identifikator shouldBe null
                        personId shouldBe PersonId(1L)
                        inntektsaar shouldBe 2025
                        kilde shouldBe SkattekortKilde.SKATTEETATEN.value
                        resultatForSkattekort shouldBe IkkeSkattekort
                        forskuddstrekkList shouldBe extensions
                        tilleggsopplysningList.shouldContainExactly(Tilleggsopplysning.OPPHOLD_PAA_SVALBARD)
                    }

                    syntetisert shouldNotBeNull {
                        generertFra shouldBe original.id
                        identifikator shouldBe null
                        personId shouldBe PersonId(1L)
                        inntektsaar shouldBe 2025
                        kilde shouldBe SkattekortKilde.SYNTETISERT.value
                        resultatForSkattekort shouldBe IkkeSkattekort
                        forskuddstrekkList.shouldContainExactly(
                            Prosentkort(Trekkode.LOENN_FRA_NAV, BigDecimal("15.70"), null),
                            Prosentkort(Trekkode.PENSJON_FRA_NAV, BigDecimal("13.10"), null),
                            Prosentkort(Trekkode.UFOERETRYGD_FRA_NAV, BigDecimal("15.70"), null),
                        )
                        tilleggsopplysningList.shouldContainExactly(Tilleggsopplysning.OPPHOLD_PAA_SVALBARD)
                    }

                    utsendingList shouldNotBeNull {
                        size shouldBe 1
                        first() shouldNotBeNull {
                            this.fnr.value shouldBe fnr
                            inntektsaar shouldBe 2025
                            forsystem shouldBe Forsystem.OPPDRAGSSYSTEMET
                            failMessage shouldBe null
                            failCount shouldBe 0
                        }
                    }

                    auditList shouldNotBeNull {
                        size shouldBe 1
                        first() shouldNotBeNull {
                            personId shouldBe PersonId(1L)
                            brukerId shouldBe "system"
                            tag shouldBe AuditTag.SYNTETISERT_SKATTEKORT
                            informasjon shouldBe "Prosentkort med default skattesatser for Svalbard syntetisert pga mottatt tilleggsinformasjon oppholdPaaSvalbard"
                        }
                    }
                }
            }
        }

        test("processSkattekortData should create only one utsending when two identical skattekort_data rows are processed") {
            databaseHas(
                aPerson(1L),
                afoedselsnummer(1L, fnr),
                anAbonnement(1L, personId = 1L, inntektsaar = 2025),
            )

            val skattekortJson =
                """
                {
                  "arbeidstakeridentifikator": "$fnr",
                  "resultatForSkattekort": "skattekortopplysningerOK",
                  "inntektsaar": 2025,
                  "skattekort": {
                    "utstedtDato": "2025-11-01",
                    "skattekortidentifikator": 10001,
                    "forskuddstrekk": []
                  }
                }
                """.trimIndent()

            // Insert the same payload twice (duplicate skattekort_data)
            tx { SkattekortDataRepository.insert(it, skattekortJson, 2025, fnr, BestillingsbatchType.BESTILLING) }
            tx { SkattekortDataRepository.insert(it, skattekortJson, 2025, fnr, BestillingsbatchType.BESTILLING) }

            skattekortDataService.processSkattekortData()

            val utsendinger = tx(UtsendingRepository::getAllUtsendinger)
            utsendinger shouldNotBeNull {
                size shouldBe 1
                first() shouldNotBeNull {
                    this.fnr.value shouldBe fnr
                    inntektsaar shouldBe 2025
                    forsystem shouldBe Forsystem.OPPDRAGSSYSTEMET
                    failMessage shouldBe null
                    failCount shouldBe 0
                }
            }
        }

        test("processSkattekortData should process remaining items in batch when one FNR is unknown") {
            // Reproduserer bug: return@transaction avbrøt hele loopen ved ukjent FNR,
            // slik at påfølgende gyldige innslag aldri ble behandlet.
            val unknownFnr = "99999999999"

            databaseHas(
                aPerson(1L),
                afoedselsnummer(1L, fnr),
                anAbonnement(1L, personId = 1L, inntektsaar = 2025),
            )

            val skattekortJson =
                """
                {
                  "arbeidstakeridentifikator": "$fnr",
                  "resultatForSkattekort": "skattekortopplysningerOK",
                  "inntektsaar": 2025,
                  "skattekort": {
                    "utstedtDato": "2025-11-01",
                    "skattekortidentifikator": 10001,
                    "forskuddstrekk": []
                  }
                }
                """.trimIndent()
            val unknownSkattekortJson =
                """
                {
                  "arbeidstakeridentifikator": "$unknownFnr",
                  "resultatForSkattekort": "skattekortopplysningerOK",
                  "inntektsaar": 2025,
                  "skattekort": {
                    "utstedtDato": "2025-11-01",
                    "skattekortidentifikator": 20002,
                    "forskuddstrekk": []
                  }
                }
                """.trimIndent()

            // Ukjent FNR legges inn FØRST — skal trigge feilhåndteringen
            tx { SkattekortDataRepository.insert(it, unknownSkattekortJson, 2025, unknownFnr, BestillingsbatchType.BESTILLING) }
            // Gyldig FNR legges inn ETTERPÅ — skal fortsatt bli behandlet
            tx { SkattekortDataRepository.insert(it, skattekortJson, 2025, fnr, BestillingsbatchType.BESTILLING) }

            skattekortDataService.processSkattekortData()

            val skattekortList = tx { SkattekortRepository.findAllByPersonId(it, listOf(PersonId(1)), listOf(2025), adminRole = true) }
            val utsendingList = tx(UtsendingRepository::getAllUtsendinger)

            assertSoftly {
                skattekortList shouldHaveSize 1
                skattekortList.first().personId shouldBe PersonId(1L)

                utsendingList shouldHaveSize 1
                utsendingList.first().fnr.value shouldBe fnr
            }
        }

        test("processSkattekortData should create one utsending OS_STOR for BESTILLING and one utsending OS for OPPDATERING") {
            databaseHas(
                aPerson(1L),
                afoedselsnummer(1L, fnr),
                anAbonnement(1L, personId = 1L, inntektsaar = 2025, forsystem = Forsystem.OPPDRAGSSYSTEMET_STOR),
            )

            val skattekortJson =
                """
                {
                  "arbeidstakeridentifikator": "$fnr",
                  "resultatForSkattekort": "skattekortopplysningerOK",
                  "inntektsaar": 2025,
                  "skattekort": {
                    "utstedtDato": "2025-11-01",
                    "skattekortidentifikator": 10001,
                    "forskuddstrekk": []
                  }
                }
                """.trimIndent()

            // Insert the same payload twice (duplicate skattekort_data)
            tx { SkattekortDataRepository.insert(it, skattekortJson, 2025, fnr, BestillingsbatchType.BESTILLING) }
            tx { SkattekortDataRepository.insert(it, skattekortJson, 2025, fnr, BestillingsbatchType.OPPDATERING) }

            skattekortDataService.processSkattekortData()

            val utsendinger = tx(UtsendingRepository::getAllUtsendinger)
            utsendinger shouldNotBeNull {
                size shouldBe 2
                first() shouldNotBeNull {
                    this.fnr.value shouldBe fnr
                    inntektsaar shouldBe 2025
                    forsystem shouldBe Forsystem.OPPDRAGSSYSTEMET_STOR
                    failMessage shouldBe null
                    failCount shouldBe 0
                }
                last() shouldNotBeNull {
                    this.fnr.value shouldBe fnr
                    inntektsaar shouldBe 2025
                    forsystem shouldBe Forsystem.OPPDRAGSSYSTEMET
                    failMessage shouldBe null
                    failCount shouldBe 0
                }
            }
        }
    })
