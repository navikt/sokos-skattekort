package no.nav.sokos.lavendel

import java.time.LocalDateTime

import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.testcontainers.toDataSource
import io.kotest.extensions.time.withConstantNow
import io.kotest.matchers.shouldBe
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.testing.testApplication
import kotliquery.queryOf
import kotliquery.sessionOf

import no.nav.security.mock.oauth2.withMockOAuth2Server
import no.nav.sokos.lavendel.config.CompositeApplicationConfig
import no.nav.sokos.lavendel.config.DatabaseConfig

class BasicEndToEndTest :
    FunSpec({
        context("simulering") {
            val container = TestContainer().container

            test("vi kan simulere en enkel AAP-request med et gyldig tabellkort (8010)") {
                withConstantNow(LocalDateTime.parse("2025-04-12T00:00:00")) {
                    withMockOAuth2Server {
                        testApplication {
                            environment {
                                config = CompositeApplicationConfig(TestUtil.getOverrides(container), ApplicationConfig("application.conf"))
                            }
                            application {
                                module()
                            }
                            startApplication()
                            TestUtil.loadDataSet("basicendtoendtest/basicdata.sql", DatabaseConfig.dataSource)
                            val dataSource = container.toDataSource()

                            sessionOf(dataSource).use {
                                val value: List<String> =
                                    it.transaction {
                                        it.run(queryOf("SELECT * FROM aktoer").map { row -> row.string("navn") }.asList)
                                    }
                                value shouldBe listOf("Signe Maten")
                            }
                        }
                    }
                }
            }
        }
    })
