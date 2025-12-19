package no.nav.sokos.skattekort.module.utsending

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.ktor.server.plugins.di.dependencies
import kotliquery.queryOf

import no.nav.sokos.skattekort.JmsTestUtil
import no.nav.sokos.skattekort.infrastructure.DbListener
import no.nav.sokos.skattekort.infrastructure.MQListener
import no.nav.sokos.skattekort.util.SQLUtils.transaction
import no.nav.sokos.skattekort.utils.TestUtils

class UtsendingEndToEndTest :
    FunSpec({
        extensions(DbListener, MQListener)

        test("vi kan plukke opp en utsending fra databasen og sende en JMS-melding med riktig format") {
            TestUtils.withFullTestApplication {
                DbListener.loadDataSet("database/skattekort/person_med_skattekort.sql")
                DbListener.loadDataSet("database/utsending/skattekort_oppdragz.sql")

                val uut: UtsendingService by application.dependencies

                uut.handleUtsending()
                val expectedCopybook =
                    "12345678903skattekortopplysningerOK                20252025-11-1119        kildeskattpensjonist                              1TrekkprosentpensjonFraNAV                                              018,50       12,0"
                eventually(TestUtils.eventuallyConfiguration) {
                    val messages: List<String> = JmsTestUtil.getMessages(MQListener.utsendingsQueue)
                    messages.size shouldBe 1
                    messages[0] shouldBe
                        expectedCopybook
                }
                DbListener.dataSource.transaction { tx ->
                    val sendinger =
                        tx.list(
                            queryOf(
                                """SELECT sending FROM bevis_sending""",
                            ),
                            { row ->
                                row.string("sending")
                            },
                        )
                    assertSoftly {
                        sendinger shouldNotBeNull {
                            size shouldBe 1
                            shouldContainAll(
                                expectedCopybook,
                            )
                        }
                    }
                }
            }
        }

        test("utsending fra databasen og sende til utsendingStor JMS kø") {
            TestUtils.withFullTestApplication {
                DbListener.loadDataSet("database/skattekort/person_med_skattekort.sql")
                DbListener.loadDataSet("database/utsending/skattekort_oppdragz_stor.sql")

                val utsendingService: UtsendingService by application.dependencies

                utsendingService.handleUtsending()
                val expectedCopybook =
                    "12345678903skattekortopplysningerOK                20252025-11-1119        kildeskattpensjonist                              1TrekkprosentpensjonFraNAV                                              018,50       12,0"
                eventually(TestUtils.eventuallyConfiguration) {
                    val messages: List<String> = JmsTestUtil.getMessages(MQListener.utsendingStorQueue)
                    messages.size shouldBe 1
                    messages[0] shouldBe expectedCopybook
                }
            }
        }
    })
