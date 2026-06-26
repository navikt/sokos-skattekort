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
import no.nav.sokos.skattekort.forespoersel.Forsystem
import no.nav.sokos.skattekort.infrastructure.tilgangsmaskin.TilgangsmaskinClientService
import no.nav.sokos.skattekort.listener.DbListener
import no.nav.sokos.skattekort.listener.DbListener.dataSource
import no.nav.sokos.skattekort.listener.WiremockListener.generateProblemDetailResponse
import no.nav.sokos.skattekort.person.Personidentifikator
import no.nav.sokos.skattekort.security.Saksbehandler
import no.nav.sokos.skattekort.skattekort.aBestilling
import no.nav.sokos.skattekort.skattekort.aBestillingsbatch
import no.nav.sokos.skattekort.skattekort.aPerson
import no.nav.sokos.skattekort.skattekort.aSkattekort
import no.nav.sokos.skattekort.skattekort.afoedselsnummer
import no.nav.sokos.skattekort.skattekort.anAbonnement
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

            val fnr = Personidentifikator("01410112345")
            val anotherFnr = Personidentifikator("01410100001")
            test("Skjermet person, skal returnere SKJERMET") {
                coEvery {
                    tilgangsmaskinClientService.checkSaksbehandlerAccess(any(), any())
                } returns
                    generateProblemDetailResponse("x123456", "12345678910")
                databaseHas()

                val status = statusService.statusForespoeresel(fnr = fnr, aar = 2025, forsystem = Forsystem.OPPDRAGSSYSTEMET, saksbehandler = saksbehandler)
                status shouldBe Status.SKJERMET
            }

            test("Nytt, gyldig og kunstig fnr som ikke kan bestille har også status IKKE_FORESPURT") {
                mockkObject(PropertiesConfig)
                every { PropertiesConfig.applicationProperties } returns mockk { every { gyldigeFnr } returns "KUNSTIGE_FNR" }

                val status = statusService.statusForespoeresel(fnr = fnr, aar = 2025, forsystem = Forsystem.OPPDRAGSSYSTEMET, saksbehandler = saksbehandler)
                status shouldBe Status.IKKE_FORESPURT
            }

            test("Ikke ekte fnr i prodlikt miljø") {
                mockkObject(PropertiesConfig)
                every { PropertiesConfig.applicationProperties } returns mockk { every { gyldigeFnr } returns "GYLDIGE" }

                val status = statusService.statusForespoeresel(fnr = fnr, aar = 2025, forsystem = Forsystem.OPPDRAGSSYSTEMET, saksbehandler = saksbehandler)
                status shouldBe Status.UGYLDIG_FNR
            }

            test("Gyldig fnr men person finnes ikke. Skal ha status IKKE_FORESPURT") {
                databaseHas()

                val status = statusService.statusForespoeresel(fnr = anotherFnr, aar = 2025, forsystem = Forsystem.OPPDRAGSSYSTEMET, saksbehandler = saksbehandler)
                status shouldBe Status.IKKE_FORESPURT
            }

            test("Person finnes, men ikke noe annet. Skal ha status ABONNERER_IKKE") {
                databaseHas(
                    aPerson(1L),
                    afoedselsnummer(1L, anotherFnr.value),
                )

                val status = statusService.statusForespoeresel(fnr = anotherFnr, aar = 2025, forsystem = Forsystem.OPPDRAGSSYSTEMET, saksbehandler = saksbehandler)
                status shouldBe Status.ABONNERER_IKKE
            }

            test("Person og en bestilling uten batch finnes. Skal ha status IKKE_BESTILT") {
                databaseHas(
                    aPerson(1L),
                    afoedselsnummer(1L, anotherFnr.value),
                    aBestilling(1L, anotherFnr.value, 2025, null),
                )

                val status = statusService.statusForespoeresel(fnr = anotherFnr, aar = 2025, forsystem = Forsystem.OPPDRAGSSYSTEMET, saksbehandler = saksbehandler)
                status shouldBe Status.IKKE_BESTILT
            }

            test("Person, bestilling og batch finnes. Skal ha status BESTILT") {
                databaseHas(
                    aPerson(1L),
                    afoedselsnummer(1L, anotherFnr.value),
                    aBestillingsbatch(1L, ref = "1234", status = NY, type = BESTILLING),
                    aBestilling(1L, anotherFnr.value, 2025, 1L),
                )

                val status = statusService.statusForespoeresel(fnr = anotherFnr, aar = 2025, forsystem = Forsystem.OPPDRAGSSYSTEMET, saksbehandler = saksbehandler)
                status shouldBe Status.BESTILT
            }

            test("Person, skattekort og utsending finnes. Skal ha status VENTER_UTSENDING") {
                databaseHas(
                    aPerson(1L),
                    afoedselsnummer(1L, anotherFnr.value),
                    aSkattekort(1L, 1L, 2025),
                    anUtsending(anotherFnr.value, 2025, forsystem = "OS", 1L),
                )

                val status = statusService.statusForespoeresel(fnr = anotherFnr, aar = 2025, forsystem = Forsystem.OPPDRAGSSYSTEMET, saksbehandler = saksbehandler)
                status shouldBe Status.VENTER_UTSENDING
            }

            test("Person og skattekort for året før finnes. Skal ha status ABONNERER_IKKE") {
                databaseHas(
                    aPerson(1L),
                    afoedselsnummer(1L, anotherFnr.value),
                    aSkattekort(1L, 1L, 2025),
                )

                val status = statusService.statusForespoeresel(fnr = anotherFnr, aar = 2026, forsystem = Forsystem.OPPDRAGSSYSTEMET, saksbehandler = saksbehandler)
                status shouldBe Status.ABONNERER_IKKE
            }
            test("Person, skattekort og abonnement for samme forsystem og år finnes. Skal ha status ABONNERER") {
                databaseHas(
                    aPerson(1L),
                    afoedselsnummer(1L, anotherFnr.value),
                    aSkattekort(1L, 1L, 2025),
                    anAbonnement(123, 1L, 2025, Forsystem.OPPDRAGSSYSTEMET),
                )

                val status = statusService.statusForespoeresel(fnr = anotherFnr, aar = 2025, forsystem = Forsystem.OPPDRAGSSYSTEMET, saksbehandler = saksbehandler)
                status shouldBe Status.ABONNERER
            }
            test("Person og abonnement for samme forsystem og år finnes. Skal ha status ABONNERER") {
                databaseHas(
                    aPerson(1L),
                    afoedselsnummer(1L, anotherFnr.value),
                    anAbonnement(123, 1L, 2025, Forsystem.OPPDRAGSSYSTEMET),
                )

                val status = statusService.statusForespoeresel(fnr = anotherFnr, aar = 2025, forsystem = Forsystem.OPPDRAGSSYSTEMET, saksbehandler = saksbehandler)
                status shouldBe Status.ABONNERER
            }
            test("Person, skattekort abonnement, og en utsending for et annet forsystem finnes. Skal ha status ABONNERER_IKKE") {
                databaseHas(
                    aPerson(1L),
                    afoedselsnummer(1L, anotherFnr.value),
                    aSkattekort(1L, 1L, 2025),
                    anUtsending(anotherFnr.value, 2025, forsystem = "THIS_IS_NOT_THE_FORSYSTEM_YOU_ARE_LOOKING_FOR", 1L),
                    anAbonnement(123, 1L, 2025, Forsystem.DARE_POC),
                )

                val status = statusService.statusForespoeresel(fnr = anotherFnr, aar = 2025, forsystem = Forsystem.OPPDRAGSSYSTEMET, saksbehandler = saksbehandler)
                status shouldBe Status.ABONNERER_IKKE
            }
        },
    )
