package no.nav.sokos.skattekort.infrastructure.tilgangsmaskin

import kotlinx.serialization.json.Json

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.http.HttpStatusCode

import no.nav.sokos.skattekort.config.createHttpClient
import no.nav.sokos.skattekort.listener.WiremockListener
import no.nav.sokos.skattekort.listener.WiremockListener.generateProblemDetailResponse
import no.nav.tilgangsmaskinen.ProblemDetailResponse

class TilgangsmaskinClientServiceTest :
    FunSpec({
        extensions(listOf(WiremockListener))
        val ansattId = "Z123456"
        val testUrl = "/api/v1/ccf/kjerne/$ansattId"

        val tilgangsmaskinClientService: TilgangsmaskinClientService by lazy {
            TilgangsmaskinClientService(
                httpClient = createHttpClient(),
                tilgangsmaskinUrl = WiremockListener.wiremock.baseUrl(),
                azuredTokenClient = WiremockListener.azuredTokenClient,
            )
        }

        test("should return statusCode 204 when saksbehandler has access") {
            WiremockListener.wiremock.stubFor(
                WireMock
                    .post(urlEqualTo(testUrl))
                    .willReturn(
                        aResponse().withStatus(HttpStatusCode.NoContent.value),
                    ),
            )

            val response = tilgangsmaskinClientService.checkSaksbehandlerAccess(ansattId, "12345678910")
            response shouldBe null
        }

        test("should return statusCode 403 when AVVIST_FORTROLIG_ADRESSE") {
            val problemDetailResponse =
                generateProblemDetailResponse(ansattId, "12345678910")
                    .copy(
                        title = ProblemDetailResponse.Title.AVVIST_FORTROLIG_ADRESSE,
                        begrunnelse = "Du har ikke tilgang til brukere med fortrolig adresse",
                    )
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

        test("should return statusCode 403 when AVVIST_STRENGT_FORTROLIG_ADRESSE") {
            val problemDetailResponse =
                generateProblemDetailResponse(ansattId, "12345678910")
                    .copy(
                        title = ProblemDetailResponse.Title.AVVIST_STRENGT_FORTROLIG_ADRESSE,
                        begrunnelse = "Du har ikke tilgang til brukere med strengt fortrolig adresse",
                    )
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

        test("should return statusCode 403 when AVVIST_SKJERMING") {
            val problemDetailResponse =
                generateProblemDetailResponse(ansattId, "12345678910")
                    .copy(
                        title = ProblemDetailResponse.Title.AVVIST_SKJERMING,
                        begrunnelse = "Du har ikke tilgang til Nav-ansatte og andre skjermede brukere",
                    )
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
    })
