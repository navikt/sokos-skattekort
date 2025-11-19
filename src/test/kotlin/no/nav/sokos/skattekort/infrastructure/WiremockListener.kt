package no.nav.sokos.skattekort.infrastructure

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.configureFor
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import io.kotest.core.listeners.AfterEachListener
import io.kotest.core.listeners.BeforeSpecListener
import io.kotest.core.spec.Spec
import io.kotest.core.test.TestCase
import io.kotest.engine.test.TestResult
import io.mockk.coEvery
import io.mockk.mockk

import no.nav.sokos.skattekort.security.AzuredTokenClient

object WiremockListener : BeforeSpecListener, AfterEachListener {
    val wiremock = WireMockServer(WireMockConfiguration.options().dynamicPort())
    val azuredTokenClient = mockk<AzuredTokenClient>()

    override suspend fun beforeSpec(spec: Spec) {
        if (!wiremock.isRunning) {
            configureFor(wiremock.port())
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
