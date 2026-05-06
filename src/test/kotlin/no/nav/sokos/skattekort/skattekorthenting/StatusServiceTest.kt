package no.nav.sokos.skattekort.skattekorthenting

import io.kotest.core.spec.style.BehaviorSpec
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
import no.nav.sokos.skattekort.skattekortbestilling.Status
import no.nav.sokos.skattekort.skattekortbestilling.StatusService

class StatusServiceTest :
    BehaviorSpec(
        {
            extensions(DbListener)

            val statusService: StatusService by lazy {
                StatusService(
                    dataSource,
                )
            }

            Given("en forespørsel med ugyldig fødselsnummer") {
                When("status forespørres") {
                    databaseHas()

                    val status = statusService.statusForespoeresel(fnr = "abc", aar = 2025, forsystem = "TEST")

                    Then("status er UGYLDIG_FNR") {
                        status shouldBe Status.UGYLDIG_FNR
                    }
                }
            }

            Given("en gyldig forespørsel der personen ikke finnes") {
                When("status forespørres") {
                    databaseHas()

                    val status = statusService.statusForespoeresel(fnr = "01010100001", aar = 2025, forsystem = "TEST")

                    Then("status er IKKE_FORESPURT") {
                        status shouldBe Status.IKKE_FORESPURT
                    }
                }
            }

            Given("en person som finnes uten bestilling eller skattekort") {
                When("status forespørres") {
                    databaseHas(
                        aPerson(1L),
                        afoedselsnummer(1L, "01010100001"),
                    )

                    val status = statusService.statusForespoeresel(fnr = "01010100001", aar = 2025, forsystem = "TEST")

                    Then("status er IKKE_FORESPURT") {
                        status shouldBe Status.IKKE_FORESPURT
                    }
                }
            }

            Given("en person med bestilling uten batch") {
                When("status forespørres") {
                    databaseHas(
                        aPerson(1L),
                        afoedselsnummer(1L, "01010100001"),
                        aBestilling(1L, "01010100001", 2025, null),
                    )

                    val status = statusService.statusForespoeresel(fnr = "01010100001", aar = 2025, forsystem = "TEST")

                    Then("status er IKKE_BESTILT") {
                        status shouldBe Status.IKKE_BESTILT
                    }
                }
            }

            Given("en person med bestilling og batch") {
                When("status forespørres") {
                    databaseHas(
                        aPerson(1L),
                        afoedselsnummer(1L, "01010100001"),
                        aBestillingsbatch(1L, ref = "1234", status = NY, type = BESTILLING),
                        aBestilling(1L, "01010100001", 2025, 1L),
                    )

                    val status = statusService.statusForespoeresel(fnr = "01010100001", aar = 2025, forsystem = "TEST")

                    Then("status er BESTILT") {
                        status shouldBe Status.BESTILT
                    }
                }
            }

            Given("en person med skattekort og et ugyldig forsystem") {
                When("status forespørres") {
                    databaseHas(
                        aPerson(1L),
                        afoedselsnummer(1L, "01010100001"),
                        aSkattekort(1L, 1L, 2025),
                    )

                    val status = statusService.statusForespoeresel(fnr = "01010100001", aar = 2025, forsystem = "TEST")

                    Then("status er UGYLDIG_FORSYSTEM") {
                        status shouldBe Status.UGYLDIG_FORSYSTEM
                    }
                }
            }

            Given("en person med skattekort og utsending for riktig forsystem") {
                When("status forespørres") {
                    databaseHas(
                        aPerson(1L),
                        afoedselsnummer(1L, "01010100001"),
                        aSkattekort(1L, 1L, 2025),
                        anUtsending("01010100001", 2025, forsystem = "OS"),
                    )

                    val status = statusService.statusForespoeresel(fnr = "01010100001", aar = 2025, forsystem = "OS")

                    Then("status er VENTER_PAA_UTSENDING") {
                        status shouldBe Status.VENTER_PAA_UTSENDING
                    }
                }
            }

            Given("en person med skattekort for året før") {
                When("status forespørres for neste år") {
                    databaseHas(
                        aPerson(1L),
                        afoedselsnummer(1L, "01010100001"),
                        aSkattekort(1L, 1L, 2025),
                    )

                    val status = statusService.statusForespoeresel(fnr = "01010100001", aar = 2026, forsystem = "OS")

                    Then("status er IKKE_FORESPURT") {
                        status shouldBe Status.IKKE_FORESPURT
                    }
                }
            }

            Given("en person med skattekort uten utsending for forespurt forsystem") {
                When("status forespørres") {
                    databaseHas(
                        aPerson(1L),
                        afoedselsnummer(1L, "01010100001"),
                        aSkattekort(1L, 1L, 2025),
                    )

                    val status = statusService.statusForespoeresel(fnr = "01010100001", aar = 2025, forsystem = "OS")

                    Then("status er SENDT_FORSYSTEM") {
                        status shouldBe Status.SENDT_FORSYSTEM
                    }
                }
            }

            Given("en person med skattekort og utsending for et annet forsystem") {
                When("status forespørres") {
                    databaseHas(
                        aPerson(1L),
                        afoedselsnummer(1L, "01010100001"),
                        aSkattekort(1L, 1L, 2025),
                        anUtsending("01010100001", 2025, forsystem = "THIS_IS_NOT_THE_FORSYSTEM_YOU_ARE_LOOKING_FOR"),
                    )

                    val status = statusService.statusForespoeresel(fnr = "01010100001", aar = 2025, forsystem = "OS")

                    Then("status er SENDT_FORSYSTEM") {
                        status shouldBe Status.SENDT_FORSYSTEM
                    }
                }
            }
        },
    )
