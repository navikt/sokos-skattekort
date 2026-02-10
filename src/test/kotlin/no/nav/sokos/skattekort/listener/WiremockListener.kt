package no.nav.sokos.skattekort.listener

import kotlinx.serialization.json.Json

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.configureFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.common.ContentTypes
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import io.kotest.core.listeners.AfterEachListener
import io.kotest.core.listeners.BeforeSpecListener
import io.kotest.core.spec.Spec
import io.kotest.core.test.TestCase
import io.kotest.engine.test.TestResult
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.mockk.coEvery
import io.mockk.mockk

import no.nav.pdl.HentIdenterBolk
import no.nav.pdl.enums.IdentGruppe
import no.nav.pdl.hentidenterbolk.HentIdenterBolkResult
import no.nav.pdl.hentidenterbolk.IdentInformasjon
import no.nav.sokos.skattekort.infrastructure.pdl.GraphQLResponse
import no.nav.sokos.skattekort.security.AzuredTokenClient

object WiremockListener : BeforeSpecListener, AfterEachListener {
    val wiremock =
        WireMockServer(WireMockConfiguration.options().dynamicPort()).apply {
            start()
        }
    val azuredTokenClient = mockk<AzuredTokenClient>()

    init {
        wiremock.start()
        configureFor(wiremock.port())
        coEvery { azuredTokenClient.getSystemToken() } returns "token"
    }

    override suspend fun beforeSpec(spec: Spec) {
        // WireMock server is already started in init block
    }

    override suspend fun afterEach(
        testCase: TestCase,
        result: TestResult,
    ) {
        wiremock.resetAll()
    }

    fun wiremockPDLStub(response: String) {
        wiremock.stubFor(
            WireMock
                .post(urlEqualTo("/graphql"))
                .willReturn(
                    aResponse()
                        .withHeader(HttpHeaders.ContentType, ContentTypes.APPLICATION_JSON)
                        .withStatus(HttpStatusCode.OK.value)
                        .withBody(response),
                ),
        )
    }

    fun generatePDLResponse(vararg fnr: String): String =
        Json.encodeToString(
            GraphQLResponse(
                HentIdenterBolk.Result(
                    hentIdenterBolk =
                        fnr.toList().map { value ->
                            HentIdenterBolkResult(
                                ident = value,
                                identer = listOf(IdentInformasjon(value, false, IdentGruppe.FOLKEREGISTERIDENT)),
                            )
                        },
                ),
            ),
        )
}
