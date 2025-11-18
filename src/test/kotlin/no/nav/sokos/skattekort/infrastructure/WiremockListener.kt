package no.nav.sokos.skattekort.infrastructure

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.configureFor
import io.kotest.core.listeners.AfterEachListener
import io.kotest.core.listeners.BeforeSpecListener
import io.kotest.core.spec.Spec
import io.kotest.core.test.TestCase
import io.kotest.engine.test.TestResult
import io.mockk.coEvery
import io.mockk.mockk

import no.nav.sokos.skattekort.security.AzuredTokenClient

private const val WIREMOCK_SERVER_PORT = 9005

object WiremockListener : BeforeSpecListener, AfterEachListener {
    val wiremock = WireMockServer(WIREMOCK_SERVER_PORT)
    val azuredTokenClient = mockk<AzuredTokenClient>()

    override suspend fun beforeSpec(spec: Spec) {
        if (!wiremock.isRunning) {
            configureFor(WIREMOCK_SERVER_PORT)
            wiremock.start()
            coEvery { azuredTokenClient.getSystemToken() } returns "token"
        }
    }

    override suspend fun afterEach(
        testCase: TestCase,
        result: TestResult,
    ) {
        wiremock.resetAll()
    }
}
