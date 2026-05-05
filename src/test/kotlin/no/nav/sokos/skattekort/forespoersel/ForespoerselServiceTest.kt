package no.nav.sokos.skattekort.forespoersel

import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch

import kotlin.time.ExperimentalTime

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.time.withConstantNow
import io.kotest.matchers.collections.shouldContainAllIgnoringFields
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

import no.nav.sokos.skattekort.infrastructure.UnleashIntegration
import no.nav.sokos.skattekort.infrastructure.pdl.PdlClientService
import no.nav.sokos.skattekort.listener.DbListener
import no.nav.sokos.skattekort.listener.WiremockListener
import no.nav.sokos.skattekort.person.AuditRepository
import no.nav.sokos.skattekort.person.AuditTag
import no.nav.sokos.skattekort.person.PersonId
import no.nav.sokos.skattekort.person.PersonService
import no.nav.sokos.skattekort.person.Personidentifikator
import no.nav.sokos.skattekort.security.Saksbehandler
import no.nav.sokos.skattekort.skattekorthenting.Bestilling
import no.nav.sokos.skattekort.util.SQLUtils.transaction
import no.nav.sokos.skattekort.utils.DBTestUtils
import no.nav.sokos.skattekort.utils.createTestHttpClient
import no.nav.sokos.skattekort.utsending.Utsending
import no.nav.sokos.skattekort.utsending.UtsendingId
import no.nav.sokos.skattekort.utsending.UtsendingRepository

@OptIn(ExperimentalTime::class)
class ForespoerselServiceTest :
    FunSpec({
        extensions(DbListener, WiremockListener)

        val pdlClientService: PdlClientService by lazy {
            PdlClientService(
                httpClient = createTestHttpClient(),
                pdlUrl = WiremockListener.wiremock.baseUrl(),
                azuredTokenClient = WiremockListener.azuredTokenClient,
            )
        }

        val personService: PersonService by lazy {
            PersonService(DbListener.dataSource, pdlClientService)
        }

        val forespoerselService: ForespoerselService by lazy {
            ForespoerselService(DbListener.dataSource, personService, UnleashIntegration())
        }

        test("taImotForespoersel skal parse message fra OS og oppretter forespoersel, abonnement, bestilling og utsending") {
            withConstantNow(LocalDateTime.parse("2025-04-12T00:00:00")) {
                WiremockListener.wiremockPDLStub(WiremockListener.generateHentIdenterBolk("01010112345"))
                val osMessage = "OS;2025;01010112345"

                forespoerselService.taImotForespoersel(osMessage)

                DbListener.dataSource.transaction { tx ->
                    val forespoerselList = ForespoerselRepository.getAllForespoersel(tx)
                    forespoerselList.size shouldBe 1
                    val forespoersel = forespoerselList.first()
                    forespoersel.forsystem shouldBe Forsystem.OPPDRAGSSYSTEMET

                    val abonnementList = AbonnementRepository.getAllAbonnementer(tx)
                    abonnementList.size shouldBe 1
                    val bestillingList = DBTestUtils.getAllBestilling(tx)
                    bestillingList.size shouldBe 1
                    val utsendingList = UtsendingRepository.getAllUtsendinger(tx)
                    utsendingList.size shouldBe 0

                    verifyData(abonnementList, bestillingList, forespoersel)
                }
            }
        }

        test("taImotForespoersel skal parse melding fra OS med flere bestillinger, og opprette forespoersel, abonnement, bestilling og utsending") {
            withConstantNow(LocalDateTime.parse("2025-04-12T00:00:00")) {
                WiremockListener.wiremockPDLStub(WiremockListener.generateHentIdenterBolk("12345678901", "23456789012"))
                val osMessage = "OS;2025;12345678901;23456789012;"
                forespoerselService.taImotForespoersel(osMessage)

                DbListener.dataSource.transaction { tx ->
                    val forespoerselList = ForespoerselRepository.getAllForespoersel(tx)
                    val abonnementList = AbonnementRepository.getAllAbonnementer(tx)
                    val bestillingList = DBTestUtils.getAllBestilling(tx)
                    val utsendingList = UtsendingRepository.getAllUtsendinger(tx)
                    assertSoftly {
                        forespoerselList shouldNotBeNull {
                            size shouldBe 1
                            shouldContainAllIgnoringFields(
                                listOf(
                                    Forespoersel(dataMottatt = "", forsystem = Forsystem.OPPDRAGSSYSTEMET_STOR),
                                    Forespoersel(dataMottatt = "", forsystem = Forsystem.OPPDRAGSSYSTEMET_STOR),
                                ),
                                Forespoersel::id,
                                Forespoersel::opprettet,
                                Forespoersel::dataMottatt,
                            )
                        }
                        abonnementList shouldNotBeNull {
                            size shouldBe 2
                        }
                        bestillingList shouldNotBeNull {
                            size shouldBe 2
                        }
                        utsendingList shouldNotBeNull {
                            size shouldBe 0
                        }
                    }
                }
            }
        }

        test("mot slutten av året skal vi også bestille for neste år") {
            withConstantNow(LocalDateTime.parse("2025-12-20T00:00:00")) {
                WiremockListener.wiremockPDLStub(WiremockListener.generateHentIdenterBolk("01010112345"))
                val osMessage = "OS;2025;01010112345"
                forespoerselService.taImotForespoersel(osMessage)

                DbListener.dataSource.transaction { tx ->
                    val forespoerselList = ForespoerselRepository.getAllForespoersel(tx)
                    forespoerselList.size shouldBe 2
                    forespoerselList.first().forsystem shouldBe Forsystem.OPPDRAGSSYSTEMET

                    val abonnementList = AbonnementRepository.getAllAbonnementer(tx)
                    abonnementList.size shouldBe 2
                    abonnementList.first().inntektsaar shouldBe 2025
                    abonnementList[1].inntektsaar shouldBe 2026

                    val bestillingList = DBTestUtils.getAllBestilling(tx)
                    bestillingList.size shouldBe 2
                    val utsendingList = UtsendingRepository.getAllUtsendinger(tx)
                    utsendingList.size shouldBe 0
                }
            }
        }

        test("taImotForespoersel skal parse message fra MANUELL og brukerId og oppretter forespoersel, abonnement, bestilling og utsending") {
            WiremockListener.wiremockPDLStub(WiremockListener.generateHentIdenterBolk("01010112345"))
            val message = "MANUELL;2026;01010112345"
            val brukerId = "Z123456"

            forespoerselService.taImotForespoersel(message, Saksbehandler(brukerId))

            DbListener.dataSource.transaction { tx ->
                val forespoerselList = ForespoerselRepository.getAllForespoersel(tx)
                forespoerselList.size shouldBe 1
                val forespoersel = forespoerselList.first()
                forespoersel.forsystem shouldBe Forsystem.MANUELL

                val abonnementList = AbonnementRepository.getAllAbonnementer(tx)
                abonnementList.size shouldBe 1
                val bestillingList = DBTestUtils.getAllBestilling(tx)
                bestillingList.size shouldBe 1
                val utsendingList = UtsendingRepository.getAllUtsendinger(tx)
                utsendingList.size shouldBe 0

                verifyData(abonnementList, bestillingList, forespoersel)

                val auditList = AuditRepository.getAuditByPersonId(tx, abonnementList.first().person.id!!)
                auditList.first().brukerId shouldBe brukerId
            }
        }

        test("taImotForespoersel med samme person og årstall som en tidligere forespoersel, skal det opprette kun en bestilling") {
            withConstantNow(LocalDateTime.parse("2025-04-12T00:00:00")) {
                WiremockListener.wiremockPDLStub(WiremockListener.generateHentIdenterBolk("01010112345"))
                val message1 = "OS;2025;01010112345"
                val message2 = "MANUELL;2025;01010112345"

                forespoerselService.taImotForespoersel(message1)
                forespoerselService.taImotForespoersel(message2)

                DbListener.dataSource.transaction { tx ->
                    val forespoerselList = ForespoerselRepository.getAllForespoersel(tx)
                    forespoerselList.size shouldBe 2
                    val abonnementList = AbonnementRepository.getAllAbonnementer(tx)
                    abonnementList.size shouldBe 2
                    val bestillingList = DBTestUtils.getAllBestilling(tx)
                    bestillingList.size shouldBe 1
                    val utsendingList = UtsendingRepository.getAllUtsendinger(tx)
                    utsendingList.size shouldBe 0

                    val auditList = AuditRepository.getAuditByPersonId(tx, abonnementList.first().person.id!!)
                    auditList[0].tag shouldBe AuditTag.OPPRETTET_PERSON
                    auditList[1].tag shouldBe AuditTag.MOTTATT_FORESPOERSEL
                }
            }
        }

        test("taImotForespoersel med samme forsystem, person og årstall som en tidligere forespoersel, skal det kun audit logges dersom en utsending ikke er utført") {
            withConstantNow(LocalDateTime.parse("2025-04-12T00:00:00")) {
                WiremockListener.wiremockPDLStub(WiremockListener.generateHentIdenterBolk("01010112345"))
                val message = "OS;2025;01010112345"

                forespoerselService.taImotForespoersel(message)
                forespoerselService.taImotForespoersel(message)

                DbListener.dataSource.transaction { tx ->
                    val forespoerselList = ForespoerselRepository.getAllForespoersel(tx)
                    forespoerselList.size shouldBe 2
                    val abonnementList = AbonnementRepository.getAllAbonnementer(tx)
                    abonnementList.size shouldBe 2
                    val bestillingList = DBTestUtils.getAllBestilling(tx)
                    bestillingList.size shouldBe 1
                    val utsendingList = UtsendingRepository.getAllUtsendinger(tx)
                    utsendingList.size shouldBe 0

                    val auditList = AuditRepository.getAuditByPersonId(tx, abonnementList.first().person.id!!)
                    auditList[0].tag shouldBe AuditTag.OPPRETTET_PERSON
                    auditList[1].tag shouldBe AuditTag.MOTTATT_FORESPOERSEL
                }
            }
        }

        test("taImotForespoersel der vi allerede har skattekort skal lage en utsending direkte") {
            DbListener.loadDataSet("database/skattekort/person_med_skattekort.sql")

            val message = "OS;2025;01010112345"

            forespoerselService.taImotForespoersel(message)

            DbListener.dataSource.transaction { tx ->
                val utsendingList = UtsendingRepository.getAllUtsendinger(tx)

                assertSoftly {
                    utsendingList shouldNotBeNull {
                        size shouldBe 1
                        shouldContainAllIgnoringFields(
                            listOf(
                                Utsending(UtsendingId(1), Personidentifikator("01010112345"), 2025, Forsystem.OPPDRAGSSYSTEMET),
                            ),
                            Utsending::opprettet,
                        )
                    }
                }
            }
        }

        test("Skal ta i mot forespørsler fra databasetabell") {
            DbListener.loadDataSet("database/forespoersler/forespoersel_fra_tabell.sql")
            WiremockListener.wiremockPDLStub(WiremockListener.generateHentIdenterBolk("19876543210"))

            forespoerselService.cronForespoerselInput()
            DbListener.dataSource.transaction { tx ->
                val bestillinger = DBTestUtils.getAllBestilling(tx)

                assertSoftly {
                    bestillinger shouldNotBeNull {
                        size shouldBe 1
                        shouldContainAllIgnoringFields(
                            listOf(
                                Bestilling(
                                    personId = PersonId(1),
                                    fnr = Personidentifikator("19876543210"),
                                    inntektsaar = 2025,
                                ),
                            ),
                            Bestilling::id,
                            Bestilling::bestillingsbatchId,
                            Bestilling::oppdatert,
                        )
                    }
                }
            }
        }

        test("skal ikke kaste en PSQLException: ERROR: duplicate key value violates unique constraint") {
            withConstantNow(LocalDateTime.parse("2025-12-20T00:00:00")) {
                WiremockListener.wiremockPDLStub(WiremockListener.generateHentIdenterBolk("01010112345"))

                val message = "OS;2025;01010112345"
                val startLatch = CountDownLatch(1)
                val completeLatch = CountDownLatch(2)
                val exceptions = ConcurrentHashMap<String, Exception>()

                val thread1 =
                    Thread {
                        try {
                            startLatch.await()
                            forespoerselService.taImotForespoersel(message)
                        } catch (e: Exception) {
                            exceptions["thread1"] = e
                        } finally {
                            completeLatch.countDown()
                        }
                    }

                val thread2 =
                    Thread {
                        try {
                            startLatch.await()
                            forespoerselService.taImotForespoersel(message)
                        } catch (e: Exception) {
                            exceptions["thread2"] = e
                        } finally {
                            completeLatch.countDown()
                        }
                    }

                thread1.start()
                thread2.start()

                // Signal both threads to start simultaneously
                startLatch.countDown()

                // Wait for completion
                completeLatch.await()

                DbListener.dataSource.transaction { tx ->
                    val forespoerselList = ForespoerselRepository.getAllForespoersel(tx)

                    exceptions.shouldBeEmpty()
                    forespoerselList.size shouldBe 4
                }
            }
        }
    })

private fun verifyData(
    abonnementList: List<Abonnement>,
    bestillingList: List<Bestilling>,
    forespoersel: Forespoersel,
) {
    val bestillingByPersonId = bestillingList.associateBy { it.personId.value }

    abonnementList.forEach { abonnement ->
        abonnement.forespoersel.id shouldBe forespoersel.id

        val bestilling =
            abonnement.person.id
                ?.value
                ?.let { bestillingByPersonId[it] }
        if (bestilling != null) {
            abonnement.person.foedselsnummer.fnr shouldBe bestilling.fnr
            abonnement.inntektsaar shouldBe bestilling.inntektsaar
            bestilling.bestillingsbatchId shouldBe null
        }
    }
}
