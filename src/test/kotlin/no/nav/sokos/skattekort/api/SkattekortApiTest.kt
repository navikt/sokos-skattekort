package no.nav.sokos.skattekort.api

import java.time.Year

import kotlinx.serialization.json.Json

import com.atlassian.oai.validator.OpenApiInteractionValidator
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs

import no.nav.sokos.skattekort.api.model.ForespoerselRequest
import no.nav.sokos.skattekort.config.ApiError
import no.nav.sokos.skattekort.forespoersel.ForespoerselService
import no.nav.sokos.skattekort.forespoersel.Forsystem
import no.nav.sokos.skattekort.skattekortbestilling.StatusService
import no.nav.sokos.skattekort.utils.ApiTestUtils.validationReport
import no.nav.sokos.skattekort.utils.oboToken
import no.nav.sokos.skattekort.utils.withApiTestApplication

private val forespoerselService: ForespoerselService = mockk()
private val statusService: StatusService = mockk()

class SkattekortApiTest :
    FunSpec({
        val validator =
            OpenApiInteractionValidator
                .createForSpecificationUrl("openapi/skattekort-v1-swagger.yaml")
                .build()

        val inntektsaar = Year.now().value

        beforeEach {
            clearMocks(forespoerselService, statusService)
        }

        test("bestille skattekort skal returnere 201 Created") {
            withApiTestApplication(
                routeSetup = { skattekortApi(forespoerselService, statusService) },
            ) { authServer, client ->
                every { forespoerselService.taImotForespoersel(any(), any()) } just runs

                val request = ForespoerselRequest(personIdent = "01010112345", aar = inntektsaar, forsystem = Forsystem.MANUELL.value)
                val response =
                    client.post("$BASE_PATH_SKATTEKORT/bestille") {
                        header(HttpHeaders.ContentType, ContentType.Application.Json)
                        header(HttpHeaders.Authorization, "Bearer ${authServer.oboToken()}")
                        setBody(request)
                    }
                val validationReport = response.validationReport(validator, HttpMethod.Post, "$BASE_PATH_SKATTEKORT/bestille", Json.encodeToString(request))

                validationReport.hasErrors() shouldBe false
                response.status shouldBe HttpStatusCode.Created
            }
        }

        test("bestille skattekort skal returnere 400 Ugyldig request med feil personIdent") {
            withApiTestApplication(
                routeSetup = { skattekortApi(forespoerselService, statusService) },
            ) { authServer, client ->
                val request = ForespoerselRequest(personIdent = "1234567", aar = inntektsaar, forsystem = Forsystem.MANUELL.value)
                val response =
                    client.post("$BASE_PATH_SKATTEKORT/bestille") {
                        header(HttpHeaders.ContentType, ContentType.Application.Json)
                        header(HttpHeaders.Authorization, "Bearer ${authServer.oboToken()}")
                        setBody(request)
                    }

                val validationReport = response.validationReport(validator, HttpMethod.Post, "$BASE_PATH_SKATTEKORT/bestille", Json.encodeToString(request))

                validationReport.hasErrors() shouldBe false
                response.status shouldBe HttpStatusCode.BadRequest

                val apiError = response.body<ApiError>()
                apiError.error shouldBe HttpStatusCode.BadRequest.description
                apiError.status shouldBe HttpStatusCode.BadRequest.value
                apiError.message shouldBe "personIdent er ugyldig. Tillatt format er 11 siffer"
                apiError.path shouldBe "$BASE_PATH_SKATTEKORT/bestille"
            }
        }

        test("bestille skattekort skal returnere 400 Ugyldig request med feil aar") {
            withApiTestApplication(
                routeSetup = { skattekortApi(forespoerselService, statusService) },
            ) { authServer, client ->
                val inntekstaar = Year.now().minusYears(2).value

                val request = ForespoerselRequest(personIdent = "01010112345", aar = inntekstaar, forsystem = Forsystem.MANUELL.value)
                val response =
                    client.post("$BASE_PATH_SKATTEKORT/bestille") {
                        header(HttpHeaders.ContentType, ContentType.Application.Json)
                        header(HttpHeaders.Authorization, "Bearer ${authServer.oboToken()}")
                        setBody(request)
                    }

                val validationReport = response.validationReport(validator, HttpMethod.Post, "$BASE_PATH_SKATTEKORT/bestille", Json.encodeToString(request))

                validationReport.hasErrors() shouldBe false
                response.status shouldBe HttpStatusCode.BadRequest

                val apiError = response.body<ApiError>()
                apiError.error shouldBe HttpStatusCode.BadRequest.description
                apiError.status shouldBe HttpStatusCode.BadRequest.value
                apiError.message shouldBe "Gyldig årstall er mellom ${Year.now().minusYears(1)} og inneværende år"
                apiError.path shouldBe "$BASE_PATH_SKATTEKORT/bestille"
            }
        }

        test("bestille skattekort skal returnere 400 Ugyldig request med feil forsystem") {
            withApiTestApplication(
                routeSetup = { skattekortApi(forespoerselService, statusService) },
            ) { authServer, client ->
                val request = ForespoerselRequest(personIdent = "01010112345", aar = inntektsaar, forsystem = "")
                val response =
                    client.post("$BASE_PATH_SKATTEKORT/bestille") {
                        header(HttpHeaders.ContentType, ContentType.Application.Json)
                        header(HttpHeaders.Authorization, "Bearer ${authServer.oboToken()}")
                        setBody(request)
                    }

                val validationReport = response.validationReport(validator, HttpMethod.Post, "$BASE_PATH_SKATTEKORT/bestille", Json.encodeToString(request))

                validationReport.hasErrors() shouldBe false
                response.status shouldBe HttpStatusCode.BadRequest

                val apiError = response.body<ApiError>()
                apiError.error shouldBe HttpStatusCode.BadRequest.description
                apiError.status shouldBe HttpStatusCode.BadRequest.value
                val gyldigeForsystemVerdier = Forsystem.entries.filterNot { it == Forsystem.OPPDRAGSSYSTEMET_STOR }.joinToString { it.value }
                apiError.message shouldBe "forsystem er ugyldig. Gyldige verdier er: $gyldigeForsystemVerdier"
                apiError.path shouldBe "$BASE_PATH_SKATTEKORT/bestille"
            }
        }
    })
