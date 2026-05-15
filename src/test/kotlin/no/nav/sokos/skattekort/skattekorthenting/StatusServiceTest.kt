package no.nav.sokos.skattekort.skattekorthenting

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject

import no.nav.sokos.skattekort.config.PropertiesConfig
import no.nav.sokos.skattekort.infrastructure.tilgangsmaskin.TilgangsmaskinClientService
import no.nav.sokos.skattekort.listener.DbListener
import no.nav.sokos.skattekort.listener.DbListener.dataSource
import no.nav.sokos.skattekort.listener.WiremockListener.generateProblemDetailResponse
import no.nav.sokos.skattekort.security.Saksbehandler
import no.nav.sokos.skattekort.skattekort.aBestilling
import no.nav.sokos.skattekort.skattekort.aBestillingsbatch
import no.nav.sokos.skattekort.skattekort.aPerson
import no.nav.sokos.skattekort.skattekort.aSkattekort
import no.nav.sokos.skattekort.skattekort.afoedselsnummer
import no.nav.sokos.skattekort.skattekort.anUtsending
import no.nav.sokos.skattekort.skattekort.databaseHas
import no.nav.sokos.skattekort.skattekortbestilling.BestillingsbatchStatus.NY
import no.nav.sokos.skattekort.skattekortbestilling.BestillingsbatchType.BESTILLING
import no.nav.sokos.skattekort.skattekortbestilling.status.Status
import no.nav.sokos.skattekort.skattekortbestilling.status.StatusService

class StatusServiceTest :
    FunSpec(
        {
            extensions(DbListener)

            val tilgangsmaskinClientService = mockk<TilgangsmaskinClientService>(relaxed = true)
            val statusService: StatusService by lazy {
                StatusService(
                    dataSource,
                    tilgangsmaskinClientService = tilgangsmaskinClientService,
                )
            }

            val saksbehandler = Saksbehandler(ident = "Z123456")

            beforeTest {
                clearAllMocks()
                coEvery {
                    tilgangsmaskinClientService.checkSaksbehandlerAccess(any(), any())
                } returns null
            }

            afterTest { unmockkObject(PropertiesConfig) }

            test("Skjermet person, skal returnere SKJERMET") {
                coEvery {
                    tilgangsmaskinClientService.checkSaksbehandlerAccess(any(), any())
                } returns
                    generateProblemDetailResponse("x123456", "12345678910")
                databaseHas()

                val status = statusService.statusForespoeresel(fnr = "01410112345", aar = 2025, forsystem = "TEST", saksbehandler = saksbehandler)
                status shouldBe Status.SKJERMET
            }

            test("Gyldig fnr men kan ikke bestille") {
                mockkObject(PropertiesConfig)
                every { PropertiesConfig.getApplicationProperties().gyldigeFnr } returns "KUNSTIGE_FNR"

                val status = statusService.statusForespoeresel(fnr = "01410112345", aar = 2025, forsystem = "TEST", saksbehandler = saksbehandler)
                status shouldBe Status.KUNSTIG_FNR
            }

            test("Ikke ekte fnr i prodlikt miljø") {
                mockkObject(PropertiesConfig)
                every { PropertiesConfig.getApplicationProperties().gyldigeFnr } returns "GYLDIGE"

                val status = statusService.statusForespoeresel(fnr = "01410112345", aar = 2025, forsystem = "TEST", saksbehandler = saksbehandler)
                status shouldBe Status.UGYLDIG_FNR
            }

            test("Ugyldig fnr. Skal ha status UGYLDIG_FNR") {
                databaseHas()

                val status = statusService.statusForespoeresel(fnr = "abc", aar = 2025, forsystem = "TEST", saksbehandler)
                status shouldBe Status.UGYLDIG_FNR
            }

            test("Gyldig fnr men person finnes ikke. Skal ha status IKKE_FORESPURT") {
                databaseHas()

                val status = statusService.statusForespoeresel(fnr = "01410100001", aar = 2025, forsystem = "TEST", saksbehandler)
                status shouldBe Status.IKKE_FORESPURT
            }

            test("Person finnes, men ikke noe annet. Skal ha status IKKE_FORESPURT") {
                databaseHas(
                    aPerson(1L),
                    afoedselsnummer(1L, "01410100001"),
                )

                val status = statusService.statusForespoeresel(fnr = "01410100001", aar = 2025, forsystem = "TEST", saksbehandler)
                status shouldBe Status.IKKE_FORESPURT
            }

            test("Person og en bestilling uten batch finnes. Skal ha status IKKE_BESTILT") {
                databaseHas(
                    aPerson(1L),
                    afoedselsnummer(1L, "01410100001"),
                    aBestilling(1L, "01410100001", 2025, null),
                )

                val status = statusService.statusForespoeresel(fnr = "01410100001", aar = 2025, forsystem = "TEST", saksbehandler)
                status shouldBe Status.IKKE_BESTILT
            }

            test("Person, bestilling og batch finnes. Skal ha status BESTILT") {
                databaseHas(
                    aPerson(1L),
                    afoedselsnummer(1L, "01410100001"),
                    aBestillingsbatch(1L, ref = "1234", status = NY, type = BESTILLING),
                    aBestilling(1L, "01410100001", 2025, 1L),
                )

                val status = statusService.statusForespoeresel(fnr = "01410100001", aar = 2025, forsystem = "TEST", saksbehandler)
                status shouldBe Status.BESTILT
            }

            test("Person og skattekort finnes. Skal ha status UGYLDIG_FORSYSTEM fordi forsystem ikke er i Forsystem-enum") {
                databaseHas(
                    aPerson(1L),
                    afoedselsnummer(1L, "01410100001"),
                    aSkattekort(1L, 1L, 2025),
                )

                val status = statusService.statusForespoeresel(fnr = "01410100001", aar = 2025, forsystem = "TEST", saksbehandler)
                status shouldBe Status.UGYLDIG_FORSYSTEM
            }

            test("Person, skattekort og utsending finnes. Skal ha status VENTER_PAA_UTSENDING") {
                databaseHas(
                    aPerson(1L),
                    afoedselsnummer(1L, "01410100001"),
                    aSkattekort(1L, 1L, 2025),
                    anUtsending("01410100001", 2025, forsystem = "OS"),
                )

                val status = statusService.statusForespoeresel(fnr = "01410100001", aar = 2025, forsystem = "OS", saksbehandler)
                status shouldBe Status.VENTER_PAA_UTSENDING
            }

            test("Person og skattekort for året før finnes. Skal ha status IKKE_FORESPURT") {
                databaseHas(
                    aPerson(1L),
                    afoedselsnummer(1L, "01410100001"),
                    aSkattekort(1L, 1L, 2025),
                )

                val status = statusService.statusForespoeresel(fnr = "01410100001", aar = 2026, forsystem = "OS", saksbehandler)
                status shouldBe Status.IKKE_FORESPURT
            }
            test("Person og skattekort finnes. Skal ha status SENDT_FORSYSTEM") {
                databaseHas(
                    aPerson(1L),
                    afoedselsnummer(1L, "01410100001"),
                    aSkattekort(1L, 1L, 2025),
                )

                val status = statusService.statusForespoeresel(fnr = "01410100001", aar = 2025, forsystem = "OS", saksbehandler)
                status shouldBe Status.SENDT_FORSYSTEM
            }
            test("Person, skattekort og utsending for et annet forsystem finnes. Skal ha status SENDT_FORSYSTEM") {
                databaseHas(
                    aPerson(1L),
                    afoedselsnummer(1L, "01410100001"),
                    aSkattekort(1L, 1L, 2025),
                    anUtsending("01410100001", 2025, forsystem = "THIS_IS_NOT_THE_FORSYSTEM_YOU_ARE_LOOKING_FOR"),
                )

                val status = statusService.statusForespoeresel(fnr = "01410100001", aar = 2025, forsystem = "OS", saksbehandler)
                status shouldBe Status.SENDT_FORSYSTEM
            }
        },
    )
