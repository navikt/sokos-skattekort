package no.nav.sokos.skattekort.utsending

import io.kotest.core.spec.style.FunSpec

class UtsendingEndToEndTest :
    FunSpec({
        // TODO: Disse E2E-testene bruker withFullTestApplication og skal skrives om.
        //  Unik dekning: eksakt copybook-format i JMS-melding, bevis_sending-tabell, og "stor" kø-ruting.
        //  UtsendingCronJobTest dekker handleUtsending + audit, men ikke format/bevis/stor-kø.

        // extensions(DbListener, MQListener)
        //
        // test("vi kan plukke opp en utsending fra databasen og sende en JMS-melding med riktig format") {
        //     withFullTestApplication {
        //         DbListener.loadDataSet("database/skattekort/person_med_skattekort.sql")
        //         DbListener.loadDataSet("database/utsending/skattekort_oppdragz.sql")
        //
        //         val uut: UtsendingService by application.dependencies
        //
        //         uut.handleUtsending()
        //         val expectedCopybook =
        //             "03030312345skattekortopplysningerOK                20252025-11-1119        kildeskattpensjonist                              1TrekkprosentpensjonFraNAV                                              018,50       12,0"
        //         eventually(eventuallyConfiguration) {
        //             val messages: List<String> = JmsTestUtil.getMessages(MQListener.utsendingsQueue)
        //             messages.size shouldBe 1
        //             messages[0] shouldBe
        //                 expectedCopybook
        //         }
        //         DbListener.dataSource.transaction { tx ->
        //             val sendinger =
        //                 tx.list(
        //                     queryOf(
        //                         """SELECT sending FROM bevis_sending""",
        //                     ),
        //                     { row ->
        //                         row.string("sending")
        //                     },
        //                 )
        //             assertSoftly {
        //                 sendinger shouldNotBeNull {
        //                     size shouldBe 1
        //                     shouldContainAll(
        //                         expectedCopybook,
        //                     )
        //                 }
        //             }
        //         }
        //     }
        // }
        //
        // test("utsending fra databasen og sende til utsendingStor JMS kø") {
        //     withFullTestApplication {
        //         DbListener.loadDataSet("database/skattekort/person_med_skattekort.sql")
        //         DbListener.loadDataSet("database/utsending/skattekort_oppdragz_stor.sql")
        //
        //         val utsendingService: UtsendingService by application.dependencies
        //
        //         utsendingService.handleUtsending()
        //         val expectedCopybook =
        //             "03030312345skattekortopplysningerOK                20252025-11-1119        kildeskattpensjonist                              1TrekkprosentpensjonFraNAV                                              018,50       12,0"
        //         eventually(eventuallyConfiguration) {
        //             val messages: List<String> = JmsTestUtil.getMessages(MQListener.utsendingStorQueue)
        //             messages.size shouldBe 1
        //             messages[0] shouldBe expectedCopybook
        //         }
        //     }
        // }
    })
