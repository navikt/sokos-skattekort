package no.nav.sokos.skattekort

import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.testcontainers.toDataSource
import org.testcontainers.containers.PostgreSQLContainer

abstract class EndToEndFunSpec(
    body: FunSpec.(dbContainer: PostgreSQLContainer<Nothing>, jmsTestServer: JmsTestServer) -> Unit,
) : FunSpec({
        val dbContainer = DbTestContainer().container
        val jmsTestServer = JmsTestServer()

        beforeTest {
            TestUtil.deleteAllTables(dbContainer.toDataSource())
        }

        afterTest {
            jmsTestServer.assertAllQueuesAreEmpty()
        }

        body(dbContainer, jmsTestServer)
    })
