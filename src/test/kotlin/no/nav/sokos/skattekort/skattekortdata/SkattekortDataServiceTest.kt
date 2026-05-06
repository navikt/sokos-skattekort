package no.nav.sokos.skattekort.skattekortdata

import java.math.BigDecimal

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.BehaviorSpec
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
import no.nav.sokos.skattekort.utils.TestUtils.readFile
import no.nav.sokos.skattekort.utils.TestUtils.tx
import no.nav.sokos.skattekort.utsending.UtsendingRepository

class SkattekortDataServiceTest :
    BehaviorSpec({
        extensions(DbListener)

        val skattekortDataService: SkattekortDataService by lazy {
            SkattekortDataService(DbListener.dataSource)
        }

        val fnr = "01010112345"

        Given("en person med abonnement men uten ubehandlede skattekortdata") {
            fun seedData() {
                databaseHas(
                    aPerson(1L),
                    afoedselsnummer(1L, fnr),
                    anAbonnement(1L, personId = 1L, inntektsaar = 2025),
                )
            }

            When("skattekortdata behandles") {
                Then("skal det ikke persisteres noen skattekort") {
                    seedData()

                    skattekortDataService.processSkattekortData()

                    val skattekort: List<Skattekort> =
                        tx {
                            SkattekortRepository.findAllByPersonId(it, PersonId(1), 2025, adminRole = true)
                        }
                    skattekort.shouldBeEmpty()
                }
            }
        }

        Given("en person med abonnement og skattekortopplysningerOK-data") {
            fun seedData() {
                databaseHas(
                    aPerson(1L),
                    afoedselsnummer(1L, fnr),
                    anAbonnement(1L, personId = 1L, inntektsaar = 2025),
                )
            }

            When("skattekortdata behandles") {
                Then("skal skattekort persisteres og utsending opprettes for abonnementet") {
                    seedData()

                    val skattekortJson = readFile("/skatteetaten/skattekortData/skattekortopplysningerOK.json")
                    tx { SkattekortDataRepository.insert(it, skattekortJson, 2025, fnr) }

                    skattekortDataService.processSkattekortData()

                    val skattekortList = tx { SkattekortRepository.findAllByPersonId(it, PersonId(1), 2025, adminRole = true) }
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
            }
        }

        Given("en person med abonnement og skattekortdata med tomt frikort") {
            fun seedData() {
                databaseHas(
                    aPerson(1L),
                    afoedselsnummer(1L, fnr),
                    anAbonnement(1L, personId = 1L, inntektsaar = 2025),
                )
            }

            When("skattekortdata behandles") {
                Then("skal tomt frikort beholdes med null som frikortbeløp") {
                    seedData()

                    val skattekortJson = readFile("/skatteetaten/skattekortData/skattekortopplysningerOK_med_tomt_frikort.json")
                    tx { SkattekortDataRepository.insert(it, skattekortJson, 2025, fnr) }

                    skattekortDataService.processSkattekortData()

                    val skattekortList = tx { SkattekortRepository.findAllByPersonId(it, PersonId(1), 2025, adminRole = true) }
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
            }
        }

        Given("en person med abonnement og skattekortdata med opphold på Svalbard") {
            fun seedData() {
                databaseHas(
                    aPerson(1L),
                    afoedselsnummer(personId = 1L, fnr = fnr),
                    anAbonnement(1L, personId = 1L, inntektsaar = 2025),
                )
            }

            When("skattekortdata behandles") {
                Then("skal tilleggsopplysninger persisteres og syntetisert skattekort opprettes") {
                    seedData()

                    val skattekortJson = readFile("/skatteetaten/skattekortData/skattekortopplysningerOK_med_oppholdPaaSvalbard.json")
                    tx { SkattekortDataRepository.insert(it, skattekortJson, 2025, fnr) }

                    skattekortDataService.processSkattekortData()

                    val skattekortList = tx { SkattekortRepository.findAllByPersonId(it, PersonId(1), 2025, adminRole = true) }
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
            }
        }

        Given("en person med abonnement og ikke trekkpliktige skattekortdata") {
            fun seedData() {
                databaseHas(
                    aPerson(1L),
                    afoedselsnummer(personId = 1L, fnr = fnr),
                    anAbonnement(1L, personId = 1L, inntektsaar = 2025),
                )
            }

            When("skattekortdata behandles") {
                Then("skal IkkeTrekkplikt persisteres og syntetisk frikort genereres") {
                    seedData()

                    val skattekortJson =
                        """
                        {
                          "arbeidstakeridentifikator": "$fnr",
                          "resultatForSkattekort": "ikkeTrekkplikt",
                          "inntektsaar": 2025
                        }
                        """.trimIndent()

                    tx { SkattekortDataRepository.insert(it, skattekortJson, 2025, fnr) }

                    skattekortDataService.processSkattekortData()

                    val skattekortList = tx { SkattekortRepository.findAllByPersonId(it, PersonId(1), 2025, adminRole = true) }
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
            }
        }

        Given("en person med abonnement og samme skattekortdata behandles to ganger") {
            fun seedData() {
                databaseHas(
                    aPerson(1L),
                    afoedselsnummer(1L, fnr),
                    anAbonnement(1L, personId = 1L, inntektsaar = 2025),
                )
            }

            When("prosesseringen kjøres to ganger") {
                Then("skal det ikke opprettes dupliserte utsendinger") {
                    seedData()

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
                    tx { SkattekortDataRepository.insert(it, skattekortJson, 2025, fnr) }

                    skattekortDataService.processSkattekortData()
                    skattekortDataService.processSkattekortData()

                    val utsendinger = tx(UtsendingRepository::getAllUtsendinger)
                    utsendinger shouldHaveSize 1
                }
            }
        }

        Given("en skattekortdatapost for en person som ikke finnes i databasen") {
            fun seedData() {
                databaseHas(
                    aPerson(1L),
                    afoedselsnummer(1L, fnr),
                    anAbonnement(1L, personId = 1L, inntektsaar = 2025),
                )
            }

            When("skattekortdata lagres") {
                Then("beholdes den eksisterende testen uten videre prosessering") {
                    seedData()

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
                    tx { SkattekortDataRepository.insert(it, skattekortJson, 2025, fnr) }
                }
            }
        }

        Given("en person med abonnement og alle tilleggsopplysninger i skattekortdata") {
            fun seedData() {
                databaseHas(
                    aPerson(1L),
                    afoedselsnummer(personId = 1L, fnr = fnr),
                    anAbonnement(1L, personId = 1L, inntektsaar = 2025),
                )
            }

            When("skattekortdata behandles") {
                Then("skal alle tilleggsopplysninger persisteres og syntetisert skattekort opprettes") {
                    seedData()

                    val skattekortJson = readFile("/skatteetaten/skattekortData/skattekortopplysningerOK_med_alle_tilleggsopplysninger.json")
                    tx { SkattekortDataRepository.insert(it, skattekortJson, 2025, fnr) }

                    skattekortDataService.processSkattekortData()

                    val skattekortList = tx { SkattekortRepository.findAllByPersonId(it, PersonId(1), 2025, adminRole = true) }
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
            }
        }

        Given("en person med abonnement og ugyldigFoedselsEllerDnummer i skattekortdata") {
            fun seedData() {
                databaseHas(
                    aPerson(1L),
                    afoedselsnummer(1L, fnr),
                    anAbonnement(1L, personId = 1L, inntektsaar = 2025),
                )
            }

            When("skattekortdata behandles") {
                Then("skal skattekortet persisteres med riktig resultat") {
                    seedData()

                    val skattekortJson =
                        """                
                        {
                          "arbeidstakeridentifikator": "$fnr",
                          "resultatForSkattekort": "ugyldigFoedselsEllerDnummer",
                          "inntektsaar": 2025
                        }                
                        """.trimIndent()
                    tx { SkattekortDataRepository.insert(it, skattekortJson, 2025, fnr) }

                    skattekortDataService.processSkattekortData()

                    val skattekortList = tx { SkattekortRepository.findAllByPersonId(it, PersonId(1), 2025, adminRole = true) }
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
            }
        }

        Given("en person med abonnement og ikkeSkattekort med opphold på Svalbard") {
            fun seedData() {
                databaseHas(
                    aPerson(1L),
                    afoedselsnummer(1L, fnr),
                    anAbonnement(1L, personId = 1L, inntektsaar = 2025),
                )
            }

            When("skattekortdata behandles") {
                Then("skal ikkeSkattekort persisteres sammen med syntetisert skattekort") {
                    seedData()

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
                    tx { SkattekortDataRepository.insert(it, skattekortJson, 2025, fnr) }

                    skattekortDataService.processSkattekortData()

                    val skattekortList = tx { SkattekortRepository.findAllByPersonId(it, PersonId(1), 2025, adminRole = true) }
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
            }
        }
    })
