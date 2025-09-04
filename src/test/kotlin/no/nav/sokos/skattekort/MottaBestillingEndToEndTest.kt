package no.nav.sokos.skattekort

import java.time.LocalDateTime

import com.zaxxer.hikari.HikariDataSource
import io.kotest.assertions.withClue
import io.kotest.extensions.testcontainers.toDataSource
import io.kotest.extensions.time.withConstantNow
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.testing.testApplication

import no.nav.security.mock.oauth2.withMockOAuth2Server
import no.nav.sokos.skattekort.aktoer.Aktoer
import no.nav.sokos.skattekort.aktoer.AktoerId
import no.nav.sokos.skattekort.aktoer.AktoerRepository
import no.nav.sokos.skattekort.aktoer.AktoerService
import no.nav.sokos.skattekort.aktoer.OffNr
import no.nav.sokos.skattekort.bestilling.Bestilling
import no.nav.sokos.skattekort.config.CompositeApplicationConfig
import no.nav.sokos.skattekort.config.DatabaseConfig

class MottaBestillingEndToEndTest :
    EndToEndFunSpec({ dbContainer, jmsTestServer ->

        test("vi kan lagre en bestilling fra OS for en aktør vi ikke har sett før") {
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
                        val aktoerService = AktoerService(DatabaseConfig.dataSource, AktoerRepository())

                        jmsTestServer.assertQueueIsEmpty(jmsTestServer.bestillingsQueue)
                        TestUtil.loadDataSet("basicendtoendtest/basicdata.sql", DatabaseConfig.dataSource)

                        val fnr = "15467834260"

                        finnOffNr(aktoerService.list(), fnr).size shouldBe 0

                        jmsTestServer.sendMessage(jmsTestServer.bestillingsQueue, "OS;2025;$fnr")

                        val dataSource: HikariDataSource = dbContainer.toDataSource()
                        val rows: List<Bestilling> = TestUtil.storedBestillings(dataSource = dataSource, whereClause = "fnr = '$fnr'")

                        finnOffNr(aktoerService.list(), fnr).size shouldBe 1

                        withClue("Forventet at det er en bestilling i databasen med fnr $fnr") {
                            rows shouldHaveSize 1
                            rows.first().fnr shouldBe fnr
                        }
                        jmsTestServer.assertQueueIsEmpty(jmsTestServer.bestillingsQueue)
                    }
                }
            }
        }
        test("vi kan lagre en bestilling fra OS for en aktør vi allerede har i databasen, og der vi allerede har en bestilling i databasen") {
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
                        val aktoerService = AktoerService(DatabaseConfig.dataSource, AktoerRepository())

                        jmsTestServer.assertQueueIsEmpty(jmsTestServer.bestillingsQueue)
                        TestUtil.loadDataSet("basicendtoendtest/basicdata.sql", DatabaseConfig.dataSource)

                        val fnr = "12345678901"

                        finnOffNr(aktoerService.list(), fnr).size shouldBe 1

                        jmsTestServer.sendMessage(jmsTestServer.bestillingsQueue, "OS;2025;$fnr")

                        val dataSource: HikariDataSource = dbContainer.toDataSource()
                        val rows: List<Bestilling> = TestUtil.storedBestillings(dataSource = dataSource, whereClause = "fnr = '$fnr'")

                        withClue("Forventet at det er en bestilling i databasen med fnr $fnr") {
                            rows shouldHaveSize 1
                            rows.first().fnr shouldBe fnr
                            rows.first().aktoer_id shouldBe AktoerId(1)
                        }
                        jmsTestServer.assertQueueIsEmpty(jmsTestServer.bestillingsQueue)
                    }
                }
            }
        }
    })

private fun finnOffNr(
    aktoerFoerKall: List<Aktoer>,
    fnr: String,
): List<OffNr> = aktoerFoerKall.flatMap { it.offNr.filter { it.aktoerIdent == fnr } }
