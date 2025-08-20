package no.nav.sokos.lavendel

import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.testcontainers.toDataSource
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.testing.testApplication
import org.testcontainers.containers.PostgreSQLContainer

import no.nav.security.mock.oauth2.withMockOAuth2Server
import no.nav.sokos.lavendel.config.CompositeApplicationConfig

abstract class EndToEndFunSpec(
    body: FunSpec.(dbContainer: PostgreSQLContainer<Nothing>, jmsTestServer: JmsTestServer) -> Unit,
) : FunSpec({
        val dbContainer = DbTestContainer().container
        val jmsTestServer = JmsTestServer()

        beforeTest {
            TestUtil.deleteAllTables(dbContainer.toDataSource())
            withMockOAuth2Server {
                testApplication {
                    environment {
                        config = CompositeApplicationConfig(TestUtil.getOverrides(dbContainer), ApplicationConfig("application.conf"))
                    }
                    application {
                        module(testJmsConnectionFactory = jmsTestServer.jmsConnectionFactory, testBestillingsQueue = jmsTestServer.bestillingsQueue)
                    }
                    startApplication()
                }
            }
        }

        afterTest {
            jmsTestServer.assertAllQueuesAreEmpty()
        }

        body(dbContainer, jmsTestServer)
    })
