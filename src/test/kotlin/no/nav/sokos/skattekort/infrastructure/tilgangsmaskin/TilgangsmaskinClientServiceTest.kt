package no.nav.sokos.skattekort.infrastructure.tilgangsmaskin

import kotlinx.serialization.json.Json

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.ktor.http.HttpStatusCode

import no.nav.sokos.skattekort.listener.WiremockListener
import no.nav.sokos.skattekort.listener.WiremockListener.generateProblemDetailResponse
import no.nav.sokos.skattekort.utils.createTestHttpClient
import no.nav.tilgangsmaskinen.ProblemDetailResponse

class TilgangsmaskinClientServiceTest :
    BehaviorSpec({
        extensions(listOf(WiremockListener))
        val ansattId = "Z123456"
        val testUrl = "/api/v1/ccf/kjerne/$ansattId"
        val foedselsnummer = "12345678910"

        val tilgangsmaskinClientService: TilgangsmaskinClientService by lazy {
            TilgangsmaskinClientService(
                httpClient = createTestHttpClient(),
                tilgangsmaskinUrl = WiremockListener.wiremock.baseUrl(),
                azuredTokenClient = WiremockListener.azuredTokenClient,
            )
        }

        Given("en tilgangsmaskin-klient som sjekker tilgang for en saksbehandler") {
            When("saksbehandler har tilgang til brukeren") {
                Then("returneres ingen problemdetaljer") {
                    WiremockListener.wiremock.stubFor(
                        WireMock
                            .post(urlEqualTo(testUrl))
                            .willReturn(
                                aResponse().withStatus(HttpStatusCode.NoContent.value),
                            ),
                    )

                    val response = tilgangsmaskinClientService.checkSaksbehandlerAccess(ansattId, foedselsnummer)
                    response shouldBe null
                }
            }

            When("tilgangsmaskinen avviser med fortrolig adresse") {
                val problemDetailResponse =
                    generateProblemDetailResponse(ansattId, "12345678910")
                        .copy(
                            title = ProblemDetailResponse.Title.AVVIST_FORTROLIG_ADRESSE,
                            begrunnelse = "Du har ikke tilgang til brukere med fortrolig adresse",
                        )

                Then("returneres problemdetaljene for fortrolig adresse") {
                    WiremockListener.wiremock.stubFor(
                        WireMock
                            .post(urlEqualTo(testUrl))
                            .willReturn(
                                aResponse()
                                    .withStatus(HttpStatusCode.Forbidden.value)
                                    .withHeader("Content-Type", "application/json")
                                    .withBody(Json.encodeToString(problemDetailResponse)),
                            ),
                    )

                    val response = tilgangsmaskinClientService.checkSaksbehandlerAccess(ansattId, "12345678910")
                    response shouldBe problemDetailResponse
                }
            }

            When("tilgangsmaskinen avviser med strengt fortrolig adresse") {
                val problemDetailResponse =
                    generateProblemDetailResponse(ansattId, "12345678910")
                        .copy(
                            title = ProblemDetailResponse.Title.AVVIST_STRENGT_FORTROLIG_ADRESSE,
                            begrunnelse = "Du har ikke tilgang til brukere med strengt fortrolig adresse",
                        )

                Then("returneres problemdetaljene for strengt fortrolig adresse") {
                    WiremockListener.wiremock.stubFor(
                        WireMock
                            .post(urlEqualTo(testUrl))
                            .willReturn(
                                aResponse()
                                    .withStatus(HttpStatusCode.Forbidden.value)
                                    .withHeader("Content-Type", "application/json")
                                    .withBody(Json.encodeToString(problemDetailResponse)),
                            ),
                    )

                    val response = tilgangsmaskinClientService.checkSaksbehandlerAccess(ansattId, "12345678910")
                    response shouldBe problemDetailResponse
                }
            }

            When("tilgangsmaskinen avviser med skjerming") {
                val problemDetailResponse =
                    generateProblemDetailResponse(ansattId, "12345678910")
                        .copy(
                            title = ProblemDetailResponse.Title.AVVIST_SKJERMING,
                            begrunnelse = "Du har ikke tilgang til Nav-ansatte og andre skjermede brukere",
                        )

                Then("returneres problemdetaljene for skjerming") {
                    WiremockListener.wiremock.stubFor(
                        WireMock
                            .post(urlEqualTo(testUrl))
                            .willReturn(
                                aResponse()
                                    .withStatus(HttpStatusCode.Forbidden.value)
                                    .withHeader("Content-Type", "application/json")
                                    .withBody(Json.encodeToString(problemDetailResponse)),
                            ),
                    )

                    val response = tilgangsmaskinClientService.checkSaksbehandlerAccess(ansattId, "12345678910")
                    response shouldBe problemDetailResponse
                }
            }
        }
    })
