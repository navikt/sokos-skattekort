package no.nav.sokos.skattekort

import java.time.LocalDateTime

import kotlin.time.Duration.Companion.seconds

import com.zaxxer.hikari.HikariDataSource
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.assertions.withClue
import io.kotest.extensions.testcontainers.toDataSource
import io.kotest.extensions.time.withConstantNow
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.testing.testApplication

import no.nav.security.mock.oauth2.withMockOAuth2Server
import no.nav.sokos.skattekort.config.CompositeApplicationConfig
import no.nav.sokos.skattekort.config.DatabaseConfig
import no.nav.sokos.skattekort.domain.Bestilling

class MottaBestillingEndToEndTest :
    EndToEndFunSpec({ dbContainer, jmsTestServer ->

        test("vi kan lagre en bestilling fra OS") {
            withConstantNow(LocalDateTime.parse("2025-04-12T00:00:00")) {
                withMockOAuth2Server {
                    testApplication {
                        environment {
                            config = CompositeApplicationConfig(TestUtil.getOverrides(dbContainer), ApplicationConfig("application.conf"))
                        }
                        application {
                            module(testJmsConnectionFactory = jmsTestServer.jmsConnectionFactory, testBestillingsQueue = jmsTestServer.bestillingsQueue)
                        }
                        startApplication()

                        jmsTestServer.assertQueueIsEmpty(jmsTestServer.bestillingsQueue)
                        TestUtil.loadDataSet("basicendtoendtest/basicdata.sql", DatabaseConfig.dataSource)

                        val fnr = "15467834260"

                        jmsTestServer.sendMessage(jmsTestServer.bestillingsQueue, "OS;1994;$fnr")

                        val dataSource: HikariDataSource = dbContainer.toDataSource()

                        eventually(1.seconds) {
                            val rows: List<Bestilling> = TestUtil.storedBestillings(dataSource = dataSource, whereClause = "fnr = '$fnr'")

                            withClue("Forventet at det er en bestilling i databasen med fnr $fnr") {
                                rows shouldHaveSize 1
                                rows.first().fnr shouldBe fnr
                            }
                        }
                        jmsTestServer.assertQueueIsEmpty(jmsTestServer.bestillingsQueue)
                    }
                }
            }
        }
    })
