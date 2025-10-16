package no.nav.sokos.skattekort

import java.time.LocalDateTime

import kotlin.time.Duration.Companion.seconds

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.assertions.nondeterministic.eventuallyConfig
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.time.withConstantNow
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.ktor.server.testing.testApplication

import no.nav.security.mock.oauth2.withMockOAuth2Server
import no.nav.sokos.skattekort.TestUtil.configureTestApplication
import no.nav.sokos.skattekort.TestUtil.configureTestEnvironment
import no.nav.sokos.skattekort.domain.forespoersel.AbonnementRepository
import no.nav.sokos.skattekort.domain.forespoersel.ForespoerselRepository
import no.nav.sokos.skattekort.domain.forespoersel.Forsystem
import no.nav.sokos.skattekort.domain.utsending.UtsendingRepository
import no.nav.sokos.skattekort.listener.DbListener
import no.nav.sokos.skattekort.listener.MQListener
import no.nav.sokos.skattekort.listener.MQListener.bestillingsQueue
import no.nav.sokos.skattekort.util.SQLUtils.transaction

class MottaBestillingEndToEndTest :
    FunSpec({
        extensions(DbListener, MQListener)

        val eventuallyConfiguration =
            eventuallyConfig {
                initialDelay = 1.seconds
                retries = 3
            }

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

                        eventually(eventuallyConfiguration) {
                            DbListener.dataSource.transaction { tx ->
                                val forespoerselList = ForespoerselRepository.getAllForespoersel(tx)

                                forespoerselList shouldHaveSize 1
                                assertSoftly {
                                    forespoerselList.first().forsystem shouldBe Forsystem.OPPDRAGSSYSTEMET
                                    forespoerselList.first().dataMottatt shouldBe "OS;1994;$fnr"
                                }

                                val abonnementList = AbonnementRepository.getAllAbonnementer(tx)

                                abonnementList shouldHaveSize 1
                                assertSoftly("Det skal ha blitt opprettet et abonnement") {
                                    abonnementList
                                        .first()
                                        .person.foedselsnummer.fnr.value shouldBe fnr
                                    abonnementList.first().inntektsaar shouldBe 1994
                                    abonnementList.first().forespoersel.forsystem shouldBe Forsystem.OPPDRAGSSYSTEMET
                                    abonnementList.first().inntektsaar shouldBe 1994
                                }

                                val utsendingList = UtsendingRepository.getAllUtsendinger(tx)

                                assertSoftly("Det skal ha blitt opprettet en utsending") {
                                    utsendingList shouldHaveSize 1
                                    val utsending = utsendingList.first()
                                    utsending.id shouldNotBe null
                                    utsending.abonnementId shouldBe abonnementList.first().id
                                    utsending.fnr.value shouldBe fnr
                                    utsending.inntektsaar shouldBe 1994
                                    utsending.forsystem shouldBe Forsystem.OPPDRAGSSYSTEMET
                                }
                            }
                        }
                    }

                    JmsTestUtil.assertQueueIsEmpty(bestillingsQueue)
                }
            }
        }
    })
