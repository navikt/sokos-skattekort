package no.nav.sokos.skattekort

// import no.nav.sokos.skattekort.ApplicationInfrastructureListener.bestillingsQueue
// import no.nav.sokos.skattekort.ApplicationInfrastructureListener.dbDataSource

import java.time.LocalDateTime

import kotlin.time.Duration.Companion.seconds

import com.zaxxer.hikari.HikariDataSource
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.time.withConstantNow
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.ktor.server.testing.testApplication

import no.nav.security.mock.oauth2.withMockOAuth2Server
import no.nav.sokos.skattekort.TestUtil.configureTestApplication
import no.nav.sokos.skattekort.TestUtil.configureTestEnvironment
import no.nav.sokos.skattekort.domain.forespoersel.Forsystem
import no.nav.sokos.skattekort.listener.DbListener
import no.nav.sokos.skattekort.listener.MQListener
import no.nav.sokos.skattekort.listener.MQListener.bestillingsQueue

class MottaBestillingEndToEndTest :
    FunSpec({
        extensions(DbListener, MQListener)

        test("vi kan håndtere en forespørsel fra OS") {
            withConstantNow(LocalDateTime.parse("2025-04-12T00:00:00")) {
                withMockOAuth2Server {
                    testApplication {
                        configureTestEnvironment()
                        configureTestApplication()
                        startApplication()

                        // Last inn SQL testdata
                        DbListener.loadDataSet("basicendtoendtest/basicdata.sql")

                        val fnr = "15467834260"
                        JmsTestUtil.sendMessage("OS;1994;$fnr")

                        eventually(1.seconds) {
                            val dataSource: HikariDataSource = DbListener.dataSource
                            val forespoersels = DbTestUtil.storedForespoersels(dataSource)

                            forespoersels shouldHaveSize 1
                            assertSoftly {
                                forespoersels.first().forsystem shouldBe Forsystem.OPPDRAGSSYSTEMET
                                forespoersels.first().dataMottatt shouldBe "OS;1994;$fnr"
                            }

                            val abonnementList = DbTestUtil.storedAbonnements(dataSource = dataSource)

                            abonnementList shouldHaveSize 1
                            assertSoftly("Det skal ha blitt opprettet et abonnement") {
                                abonnementList
                                    .first()
                                    .person.foedselsnummer.fnr.value shouldBe fnr
                                abonnementList.first().inntektsaar shouldBe 1994
                                abonnementList.first().forespoersel.forsystem shouldBe Forsystem.OPPDRAGSSYSTEMET
                                abonnementList.first().inntektsaar shouldBe 1994
                            }

                            val utsendinger = DbTestUtil.storedUtsendingerAsText(dataSource)

                            assertSoftly("Det skal ha blitt opprettet en utsending") {
                                utsendinger shouldHaveSize 1
                                utsendinger.first() shouldBe "1" + "-" + fnr + "-" + Forsystem.OPPDRAGSSYSTEMET.kode + "-" + "1994"
                            }
                        }
                    }
                    JmsTestUtil.assertQueueIsEmpty(bestillingsQueue)
                }
            }
        }
    })
