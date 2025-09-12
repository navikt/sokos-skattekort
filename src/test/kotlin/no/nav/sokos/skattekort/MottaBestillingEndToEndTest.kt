package no.nav.sokos.skattekort

import java.time.LocalDateTime

import kotlin.time.Duration.Companion.seconds

import com.zaxxer.hikari.HikariDataSource
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
import no.nav.sokos.skattekort.bestilling.Bestilling
import no.nav.sokos.skattekort.config.CompositeApplicationConfig
import no.nav.sokos.skattekort.config.DatabaseConfig

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
                            val rows: List<Bestilling> = DbTestUtil.storedBestillings(dataSource = dataSource, whereClause = "fnr = '$fnr'")

                            withClue("Forventet at det er en bestilling i databasen med fnr $fnr") {
                                rows shouldHaveSize 1
                                rows.first().fnr shouldBe fnr
                            }
                        }
                        JmsTestUtil.assertQueueIsEmpty(bestillingsQueue())
                    }
                }
            }
        }
    })
