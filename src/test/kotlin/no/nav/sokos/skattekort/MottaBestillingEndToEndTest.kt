package no.nav.sokos.skattekort

import io.kotest.core.spec.style.FunSpec

class MottaBestillingEndToEndTest :
    FunSpec({
        // TODO: Disse E2E-testene bruker withFullTestApplication og skal skrives om.
        //  Unik dekning: JMS-melding → listener → ForespoerselService → DB (hele flyten).
        //  ForespoerselServiceTest dekker service-laget, men ikke MQ-listener-integrasjonen.

        // extensions(DbListener, MQListener, WiremockListener)
        //
        // test("vi kan håndtere en forespørsel fra OS") {
        //     withConstantNow(LocalDateTime.parse("2025-04-12T00:00:00")) {
        //         withFullTestApplication {
        //             // Last inn SQL testdata
        //             DbListener.loadDataSet("basicendtoendtest/basicdata.sql")
        //
        //             val fnr = "15467834260"
        //             WiremockListener.wiremockPDLStub(WiremockListener.generateHentIdenterBolk(fnr))
        //             JmsTestUtil.sendMessage(msg = "OS;2027;$fnr", queue = forespoerselQueue)
        //
        //             eventually(eventuallyConfiguration) {
        //                 DbListener.dataSource.transaction { tx ->
        //                     val forespoerselList = ForespoerselRepository.getAllForespoersel(tx)
        //
        //                     forespoerselList shouldHaveSize 1
        //                     assertSoftly {
        //                         forespoerselList.first().forsystem shouldBe Forsystem.OPPDRAGSSYSTEMET
        //                         forespoerselList.first().dataMottatt shouldBe "OS;2027;$fnr"
        //                     }
        //
        //                     val abonnementList = AbonnementRepository.getAllAbonnementer(tx)
        //
        //                     abonnementList shouldHaveSize 1
        //                     assertSoftly("Det skal ha blitt opprettet et abonnement") {
        //                         abonnementList
        //                             .first()
        //                             .person.foedselsnummer.fnr.value shouldBe fnr
        //                         abonnementList.first().inntektsaar shouldBe 2027
        //                         abonnementList.first().forespoersel.forsystem shouldBe Forsystem.OPPDRAGSSYSTEMET
        //                         abonnementList.first().inntektsaar shouldBe 2027
        //                     }
        //
        //                     val utsendingList = UtsendingRepository.getAllUtsendinger(tx)
        //
        //                     assertSoftly("Det skal ikke ha blitt opprettet en utsending") {
        //                         utsendingList shouldHaveSize 0
        //                     }
        //                 }
        //             }
        //             JmsTestUtil.assertQueueIsEmpty(forespoerselQueue)
        //         }
        //     }
        // }
    })
