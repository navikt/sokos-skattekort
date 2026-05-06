package no.nav.sokos.skattekort.infrastructure.pdl

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.common.ContentTypes
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode

import no.nav.pdl.enums.IdentGruppe
import no.nav.sokos.skattekort.listener.WiremockListener
import no.nav.sokos.skattekort.utils.TestUtils.readFile
import no.nav.sokos.skattekort.utils.createTestHttpClient

internal class PdlClientServiceTest :
    BehaviorSpec({
        extensions(listOf(WiremockListener))

        val pdlClientService: PdlClientService by lazy {
            PdlClientService(
                httpClient = createTestHttpClient(),
                pdlUrl = WiremockListener.wiremock.baseUrl(),
                azuredTokenClient = WiremockListener.azuredTokenClient,
            )
        }

        Given("en PDL-klient som henter identer i bulk") {
            val identerFunnetOkResponse = readFile("/pdl/hentIdenterBolkOkResponse.json")
            val identerFunnetFeilResponse = readFile("/pdl/hentIdenterBolkFeilResponse.json")
            val ikkeAutentisertResponse = readFile("/pdl/ikkeAutentisertResponse.json")

            When("PDL svarer med identer") {
                Then("returneres alle identene med tilhørende metadata") {
                    WiremockListener.wiremock.stubFor(
                        WireMock
                            .post(urlEqualTo("/graphql"))
                            .willReturn(
                                aResponse()
                                    .withHeader(HttpHeaders.ContentType, ContentTypes.APPLICATION_JSON)
                                    .withStatus(HttpStatusCode.OK.value)
                                    .withBody(identerFunnetOkResponse),
                            ),
                    )

                    val response = pdlClientService.getIdenterBolk(listOf("12345678912", "01111953488", "40074203226"))

                    response.size shouldBe 3

                    response["24519539620"]?.size shouldBe 2
                    response["24519539620"]?.get(0)?.historisk shouldBe true
                    response["24519539620"]?.get(1)?.historisk shouldBe false
                    response["24519539620"]?.get(0)?.gruppe shouldBe IdentGruppe.FOLKEREGISTERIDENT

                    response["01111953488"]?.size shouldBe 1
                    response["01111953488"]?.get(0)?.historisk shouldBe false
                    response["01111953488"]?.get(0)?.gruppe shouldBe IdentGruppe.FOLKEREGISTERIDENT

                    response["40074203226"]?.size shouldBe 1
                    response["40074203226"]?.get(0)?.historisk shouldBe false
                    response["40074203226"]?.get(0)?.gruppe shouldBe IdentGruppe.FOLKEREGISTERIDENT
                }
            }

            When("forespørselen sendes uten identer") {
                Then("kastes PdlException med valideringsfeilen") {
                    WiremockListener.wiremock.stubFor(
                        WireMock
                            .post(urlEqualTo("/graphql"))
                            .willReturn(
                                aResponse()
                                    .withHeader(HttpHeaders.ContentType, ContentTypes.APPLICATION_JSON)
                                    .withStatus(HttpStatusCode.OK.value)
                                    .withBody(identerFunnetFeilResponse),
                            ),
                    )

                    val exception =
                        shouldThrow<PdlException> {
                            pdlClientService.getIdenterBolk(emptyList())
                        }

                    exception.message shouldBe "Message: Ingen identer angitt."
                }
            }

            When("PDL svarer at klienten ikke er autentisert") {
                Then("kastes PdlException med autentiseringsfeilen") {
                    WiremockListener.wiremock.stubFor(
                        WireMock
                            .post(urlEqualTo("/graphql"))
                            .willReturn(
                                aResponse()
                                    .withHeader(HttpHeaders.ContentType, ContentTypes.APPLICATION_JSON)
                                    .withStatus(HttpStatusCode.OK.value)
                                    .withBody(ikkeAutentisertResponse),
                            ),
                    )

                    val exception =
                        shouldThrow<PdlException> {
                            pdlClientService.getIdenterBolk(listOf("12345678912"))
                        }

                    exception.message shouldBe "Message: Ikke autentisert"
                }
            }
        }
    })
