package no.nav.sokos.skattekort

import java.time.LocalDateTime

import kotlin.time.Duration.Companion.seconds

import com.zaxxer.hikari.HikariDataSource
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.time.withConstantNow
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.testing.testApplication

import no.nav.security.mock.oauth2.withMockOAuth2Server
import no.nav.sokos.skattekort.ApplicationInfrastructureListener.bestillingsQueue
import no.nav.sokos.skattekort.ApplicationInfrastructureListener.dbContainer
import no.nav.sokos.skattekort.ApplicationInfrastructureListener.dbDataSource
import no.nav.sokos.skattekort.ApplicationInfrastructureListener.jmsConnectionFactory
import no.nav.sokos.skattekort.config.CompositeApplicationConfig
import no.nav.sokos.skattekort.config.DatabaseConfig
import no.nav.sokos.skattekort.domain.forespoersel.Forespoersel
import no.nav.sokos.skattekort.domain.forespoersel.Forsystem

class MottaBestillingEndToEndTest :
    FunSpec({
        extension(ApplicationInfrastructureListener)

        test("vi kan lagre en bestilling fra OS") {
            withConstantNow(LocalDateTime.parse("2025-04-12T00:00:00")) {
                withMockOAuth2Server {
                    testApplication {
                        environment {
                            config = CompositeApplicationConfig(DbTestUtil.getOverrides(dbContainer()), ApplicationConfig("application.conf"))
                        }
                        application {
                            module(testJmsConnectionFactory = jmsConnectionFactory(), testBestillingsQueue = bestillingsQueue())
                        }
                        startApplication()

                        JmsTestUtil.assertQueueIsEmpty(bestillingsQueue())
                        DbTestUtil.loadDataSet("basicendtoendtest/basicdata.sql", DatabaseConfig.dataSource)

                        val fnr = "15467834260"
                        JmsTestUtil.sendMessage("OS;1994;$fnr")

                        eventually(1.seconds) {
                            val dataSource: HikariDataSource = dbDataSource()
                            val rows: List<Forespoersel> = DbTestUtil.storedForespoersels(dataSource = dataSource)

                            withClue("Forventet at det er en forespørsel i databasen") {
                                rows shouldHaveSize 1
                            }

                            val forespoersels = DbTestUtil.storedForespoersels(dataSource)

                            forespoersels shouldHaveSize 1
                            assertSoftly {
                                forespoersels.first().forsystem shouldBe Forsystem.OPPDRAGSSYSTEMET
                                forespoersels.first().dataMottatt shouldBe "OS;1994;$fnr"
                            }

                            val skattekortforespoersler = DbTestUtil.storedSkattekortforespoersler(dataSource = dataSource)

                            skattekortforespoersler shouldHaveSize 1
                            assertSoftly {
                                skattekortforespoersler
                                    .first()
                                    .person.foedselsnummer.fnr.value shouldBe fnr
                                skattekortforespoersler.first().aar shouldBe 1994
                                skattekortforespoersler.first().forespoersel.forsystem shouldBe Forsystem.OPPDRAGSSYSTEMET
                                skattekortforespoersler.first().aar shouldBe 1994
                            }
                        }
                    }
                    JmsTestUtil.assertQueueIsEmpty(bestillingsQueue())
                }
            }
        }
    })
