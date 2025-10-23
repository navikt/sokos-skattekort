package no.nav.sokos.skattekort

import java.time.LocalDateTime

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.time.withConstantNow
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

import no.nav.sokos.skattekort.TestUtil.eventuallyConfiguration
import no.nav.sokos.skattekort.TestUtil.withFullTestApplication
import no.nav.sokos.skattekort.listener.DbListener
import no.nav.sokos.skattekort.listener.MQListener
import no.nav.sokos.skattekort.listener.MQListener.bestillingsQueue
import no.nav.sokos.skattekort.module.forespoersel.AbonnementRepository
import no.nav.sokos.skattekort.module.forespoersel.ForespoerselRepository
import no.nav.sokos.skattekort.module.forespoersel.Forsystem
import no.nav.sokos.skattekort.module.utsending.UtsendingRepository
import no.nav.sokos.skattekort.util.SQLUtils.transaction

class MottaBestillingEndToEndTest :
    FunSpec({
        extensions(DbListener, MQListener)

        test("vi kan håndtere en forespørsel fra OS") {
            withConstantNow(LocalDateTime.parse("2025-04-12T00:00:00")) {
                withFullTestApplication {
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
                    JmsTestUtil.assertQueueIsEmpty(bestillingsQueue)
                }
            }
        }
    })
