package no.nav.sokos.skattekort.forespoersel

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.time.withConstantNow
import io.kotest.matchers.collections.shouldContainAllIgnoringFields
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.mockk
import org.slf4j.LoggerFactory

import no.nav.sokos.skattekort.config.createHttpClient
import no.nav.sokos.skattekort.infrastructure.pdl.PdlClientService
import no.nav.sokos.skattekort.infrastructure.tilgangsmaskin.TilgangsmaskinClientService
import no.nav.sokos.skattekort.listener.DbListener
import no.nav.sokos.skattekort.listener.WiremockListener
import no.nav.sokos.skattekort.person.AuditRepository
import no.nav.sokos.skattekort.person.AuditTag
import no.nav.sokos.skattekort.person.PersonService
import no.nav.sokos.skattekort.person.Personidentifikator
import no.nav.sokos.skattekort.security.Saksbehandler
import no.nav.sokos.skattekort.skattekort.SkattekortId
import no.nav.sokos.skattekort.skattekorthenting.Bestilling
import no.nav.sokos.skattekort.skattekorthenting.BestillingRepository
import no.nav.sokos.skattekort.util.SQLUtils.transaction
import no.nav.sokos.skattekort.utils.DBTestUtils
import no.nav.sokos.skattekort.utils.DateUtils.toLocalDate
import no.nav.sokos.skattekort.utsending.Utsending
import no.nav.sokos.skattekort.utsending.UtsendingId
import no.nav.sokos.skattekort.utsending.UtsendingRepository

class ForespoerselServiceTest :
    FunSpec({
        extensions(DbListener, WiremockListener)

        val logger = LoggerFactory.getLogger(ForespoerselService::class.java) as Logger
        val listAppender = ListAppender<ILoggingEvent>().apply { start() }

        beforeSpec {
            logger.addAppender(listAppender)
        }

        beforeEach {
            listAppender.list.clear()
        }

        afterSpec {
            logger.detachAppender(listAppender)
            listAppender.stop()
        }

        val pdlClientService: PdlClientService by lazy {
            PdlClientService(
                httpClient = createHttpClient(),
                pdlUrl = WiremockListener.wiremock.baseUrl(),
                azuredTokenClient = WiremockListener.azuredTokenClient,
            )
        }

        val personService: PersonService by lazy {
            PersonService(DbListener.dataSource, pdlClientService, mockk<TilgangsmaskinClientService>(relaxed = true))
        }

        val forespoerselService: ForespoerselService by lazy {
            ForespoerselService(DbListener.dataSource, personService)
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
                val osMessage = "OS;2026;12345678901;23456789012;"
                forespoerselService.taImotForespoersel(osMessage)

                DbListener.dataSource.transaction { tx ->
                    val forespoerselList = ForespoerselRepository.getAllForespoersel(tx)
                    val abonnementList = AbonnementRepository.getAllAbonnementer(tx)
                    val bestillingList = DBTestUtils.getAllBestilling(tx)
                    val utsendingList = UtsendingRepository.getAllUtsendinger(tx)
                    assertSoftly {
                        forespoerselList shouldNotBeNull {
                            size shouldBe 1
                            first() shouldNotBeNull {
                                id shouldBe ForespoerselId(1)
                                dataMottatt shouldBe osMessage
                                forsystem shouldBe Forsystem.OPPDRAGSSYSTEMET_STOR
                                opprettet.toLocalDate() shouldBe LocalDate.now()
                            }
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

        test("taImotForespoersel skal parse melding fra OS med flere bestillinger, og opprette forespoersel, abonnement, bestilling og utsending etter 15.12") {
            withConstantNow(LocalDateTime.parse("2025-12-20T00:00:00")) {
                WiremockListener.wiremockPDLStub(WiremockListener.generateHentIdenterBolk("12345678901", "23456789012"))
                val osMessage = "OS;2026;12345678901;23456789012;"
                forespoerselService.taImotForespoersel(osMessage)

                DbListener.dataSource.transaction { tx ->
                    val forespoerselList = ForespoerselRepository.getAllForespoersel(tx)
                    val abonnementList = AbonnementRepository.getAllAbonnementer(tx)
                    val bestillingList = DBTestUtils.getAllBestilling(tx)
                    val utsendingList = UtsendingRepository.getAllUtsendinger(tx)
                    assertSoftly {
                        forespoerselList shouldNotBeNull {
                            size shouldBe 1
                            first() shouldNotBeNull {
                                id shouldBe ForespoerselId(1)
                                dataMottatt shouldBe osMessage
                                forsystem shouldBe Forsystem.OPPDRAGSSYSTEMET_STOR
                                opprettet shouldNotBe null
                            }
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

        test("Forespørres fjoråret med et fnr som vi har skattekort for, skal vi opprette utsending") {
            withConstantNow(LocalDateTime.parse("2026-07-01T00:00:01")) {
                DbListener.loadDataSet("database/skattekort/person_med_skattekort.sql")

                WiremockListener.wiremockPDLStub(WiremockListener.generateHentIdenterBolk("01010112345"))
                val osMessage = "OS;2025;01010112345"
                forespoerselService.taImotForespoersel(osMessage)

                DbListener.dataSource.transaction { tx ->
                    val abonnementList = AbonnementRepository.getAllAbonnementer(tx)
                    abonnementList.size shouldBe 1
                    abonnementList.first().inntektsaar shouldBe 2025
                    val bestillingList = DBTestUtils.getAllBestilling(tx)
                    bestillingList.size shouldBe 0
                    val utsendingList = UtsendingRepository.getAllUtsendinger(tx)
                    utsendingList.size shouldBe 1
                }
            }
        }
        test("Forespørres fjoråret med et fnr som vi ikke har skattekort for, skal vi opprette abonnement") {
            withConstantNow(LocalDateTime.parse("2026-07-01T00:00:01")) {
                WiremockListener.wiremockPDLStub(WiremockListener.generateHentIdenterBolk("01010112345"))
                val osMessage = "OS;2025;01010112345"
                forespoerselService.taImotForespoersel(osMessage)

                DbListener.dataSource.transaction { tx ->
                    val abonnementList = AbonnementRepository.getAllAbonnementer(tx)
                    withClue("Vi skal opprette abonnement for 2025 selv om vi ikke kan bestille") {
                        abonnementList.size shouldBe 1
                        abonnementList.first().inntektsaar shouldBe 2025
                    }
                    withClue("Vi skal ikke opprette bestilling når vi ikke kan bestille fra Skatteetaten") {
                        val bestillingList = DBTestUtils.getAllBestilling(tx)
                        bestillingList.size shouldBe 0
                    }
                    withClue("Vi skal ikke opprette utsending når vi ikke har noe skattekort å sende ut") {
                        val utsendingList = UtsendingRepository.getAllUtsendinger(tx)
                        utsendingList.size shouldBe 0
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
                    forespoerselList shouldNotBeNull {
                        size shouldBe 2
                        first() shouldNotBeNull {
                            id shouldBe ForespoerselId(1)
                            dataMottatt shouldBe osMessage
                            forsystem shouldBe Forsystem.OPPDRAGSSYSTEMET
                            opprettet shouldNotBe null
                        }
                        last() shouldNotBeNull {
                            id shouldBe ForespoerselId(2)
                            dataMottatt shouldBe "OS;2026;01010112345"
                            forsystem shouldBe Forsystem.OPPDRAGSSYSTEMET
                            opprettet shouldNotBe null
                        }
                    }

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
            withConstantNow(LocalDateTime.parse("2025-06-20T00:00:00")) {
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
                                    Utsending(
                                        id = UtsendingId(1),
                                        fnr = Personidentifikator("01010112345"),
                                        inntektsaar = 2025,
                                        forsystem = Forsystem.OPPDRAGSSYSTEMET,
                                        skattekortId = SkattekortId(1),
                                    ),
                                ),
                                Utsending::opprettet,
                            )
                        }
                    }
                }
            }
        }

        test("skal ikke kaste en PSQLException: ERROR: duplicate key value violates unique constraint") {
            withConstantNow(LocalDateTime.parse("2025-12-20T00:00:00")) {
                WiremockListener.wiremockPDLStub(WiremockListener.generateHentIdenterBolk("01010112345"))

                val message = "OS;2025;01010112345"
                val exceptions = ConcurrentHashMap<String, Exception>()

                coroutineScope {
                    launch(Dispatchers.Default) {
                        try {
                            forespoerselService.taImotForespoersel(message)
                        } catch (e: Exception) {
                            exceptions["job1"] = e
                        }
                    }
                    launch(Dispatchers.Default) {
                        try {
                            forespoerselService.taImotForespoersel(message)
                        } catch (e: Exception) {
                            exceptions["job2"] = e
                        }
                    }
                }

                DbListener.dataSource.transaction { tx ->
                    val forespoerselList = ForespoerselRepository.getAllForespoersel(tx)

                    exceptions.shouldBeEmpty()
                    forespoerselList.size shouldBe 4
                }
            }
        }

        test("taImotForespoersel der bestilling allerede finnes i DB skal logge bestillingCount 0 pga ON CONFLICT DO NOTHING") {
            withConstantNow(LocalDateTime.parse("2025-06-20T00:00:00")) {
                DbListener.loadDataSet("database/forespoersler/forespoersel_med_bestilling.sql")
                val fnr = "01010112345"

                DbListener.dataSource.transaction { tx ->
                    BestillingRepository
                        .getAllBestillingsForAdmin(tx)
                        .first()
                        .fnr.value shouldBe fnr
                }
                val osMessage = "OS;2025;$fnr"

                forespoerselService.taImotForespoersel(osMessage)

                val logMessage = listAppender.list.map { it.formattedMessage }.first { it.startsWith("ForespoerselId:") }
                logMessage shouldBe "ForespoerselId: 1 med total: 1 abonnement(er), 0 bestilling(er), 0 utsending(er) for inntektsår: 2025"
            }
        }

        test("taImotForespoersel der utsending allerede finnes i DB skal legge utsendingCount 0 pga ON CONFLICT DO NOTHING") {
            withConstantNow(LocalDateTime.parse("2025-06-20T00:00:00")) {
                DbListener.loadDataSet("database/forespoersler/forespoersel_med_utsending.sql")

                val fnr = "01010112345"

                DbListener.dataSource.transaction { tx ->
                    UtsendingRepository
                        .getAllUtsendinger(tx)
                        .first()
                        .fnr.value shouldBe fnr
                }
                val message = "OS;2025;$fnr"
                forespoerselService.taImotForespoersel(message)

                val logMessage = listAppender.list.map { it.formattedMessage }.first { it.startsWith("ForespoerselId:") }
                logMessage shouldBe "ForespoerselId: 1 med total: 1 abonnement(er), 0 bestilling(er), 0 utsending(er) for inntektsår: 2025"
            }
        }

        test("en forespørsel i ikke-støttet XML-format") {
            val message = "<xml>ikke-stottet</xml>"
            forespoerselService.taImotForespoersel(message)
            val logMessage = listAppender.list.map { it.formattedMessage }.first { it.startsWith("Ikke støttet innlesningsformat") }
            logMessage shouldBe "Ikke støttet innlesningsformat til skattekort"
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
