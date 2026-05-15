package no.nav.sokos.skattekort.skattekorthenting

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

import no.nav.sokos.skattekort.listener.DbListener
import no.nav.sokos.skattekort.listener.DbListener.dataSource
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

            val statusService: StatusService by lazy {
                StatusService(
                    dataSource,
                )
            }

            test("Ugyldig fnr. Skal ha status UGYLDIG_FNR") {
                databaseHas()

                val status = statusService.statusForespoeresel(fnr = "abc", aar = 2025, forsystem = "TEST")
                status shouldBe Status.UGYLDIG_FNR
            }

            test("Gyldig fnr men person finnes ikke. Skal ha status IKKE_FORESPURT") {
                databaseHas()

                val status = statusService.statusForespoeresel(fnr = "01010100001", aar = 2025, forsystem = "TEST")
                status shouldBe Status.IKKE_FORESPURT
            }

            test("Person finnes, men ikke noe annet. Skal ha status IKKE_FORESPURT") {
                databaseHas(
                    aPerson(1L),
                    afoedselsnummer(1L, "01010100001"),
                )

                val status = statusService.statusForespoeresel(fnr = "01010100001", aar = 2025, forsystem = "TEST")
                status shouldBe Status.IKKE_FORESPURT
            }

            test("Person og en bestilling uten batch finnes. Skal ha status IKKE_BESTILT") {
                databaseHas(
                    aPerson(1L),
                    afoedselsnummer(1L, "01010100001"),
                    aBestilling(1L, "01010100001", 2025, null),
                )

                val status = statusService.statusForespoeresel(fnr = "01010100001", aar = 2025, forsystem = "TEST")
                status shouldBe Status.IKKE_BESTILT
            }

            test("Person, bestilling og batch finnes. Skal ha status BESTILT") {
                databaseHas(
                    aPerson(1L),
                    afoedselsnummer(1L, "01010100001"),
                    aBestillingsbatch(1L, ref = "1234", status = NY, type = BESTILLING),
                    aBestilling(1L, "01010100001", 2025, 1L),
                )

                val status = statusService.statusForespoeresel(fnr = "01010100001", aar = 2025, forsystem = "TEST")
                status shouldBe Status.BESTILT
            }

            test("Person og skattekort finnes. Skal ha status UGYLDIG_FORSYSTEM fordi forsystem ikke er i Forsystem-enum") {
                databaseHas(
                    aPerson(1L),
                    afoedselsnummer(1L, "01010100001"),
                    aSkattekort(1L, 1L, 2025),
                )

                val status = statusService.statusForespoeresel(fnr = "01010100001", aar = 2025, forsystem = "TEST")
                status shouldBe Status.UGYLDIG_FORSYSTEM
            }

            test("Person, skattekort og utsending finnes. Skal ha status VENTER_PAA_UTSENDING") {
                databaseHas(
                    aPerson(1L),
                    afoedselsnummer(1L, "01010100001"),
                    aSkattekort(1L, 1L, 2025),
                    anUtsending("01010100001", 2025, forsystem = "OS"),
                )

                val status = statusService.statusForespoeresel(fnr = "01010100001", aar = 2025, forsystem = "OS")
                status shouldBe Status.VENTER_PAA_UTSENDING
            }

            test("Person og skattekort for året før finnes. Skal ha status IKKE_FORESPURT") {
                databaseHas(
                    aPerson(1L),
                    afoedselsnummer(1L, "01010100001"),
                    aSkattekort(1L, 1L, 2025),
                )

                val status = statusService.statusForespoeresel(fnr = "01010100001", aar = 2026, forsystem = "OS")
                status shouldBe Status.IKKE_FORESPURT
            }
            test("Person og skattekort finnes. Skal ha status SENDT_FORSYSTEM") {
                databaseHas(
                    aPerson(1L),
                    afoedselsnummer(1L, "01010100001"),
                    aSkattekort(1L, 1L, 2025),
                )

                val status = statusService.statusForespoeresel(fnr = "01010100001", aar = 2025, forsystem = "OS")
                status shouldBe Status.SENDT_FORSYSTEM
            }
            test("Person, skattekort og utsending for et annet forsystem finnes. Skal ha status SENDT_FORSYSTEM") {
                databaseHas(
                    aPerson(1L),
                    afoedselsnummer(1L, "01010100001"),
                    aSkattekort(1L, 1L, 2025),
                    anUtsending("01010100001", 2025, forsystem = "THIS_IS_NOT_THE_FORSYSTEM_YOU_ARE_LOOKING_FOR"),
                )

                val status = statusService.statusForespoeresel(fnr = "01010100001", aar = 2025, forsystem = "OS")
                status shouldBe Status.SENDT_FORSYSTEM
            }
        },
    )
