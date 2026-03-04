package no.nav.sokos.skattekort.api

import java.time.LocalDateTime
import java.time.Year

import kotlin.time.ExperimentalTime
import kotlinx.serialization.json.Json

import com.atlassian.oai.validator.OpenApiInteractionValidator
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.time.withConstantNow
import io.kotest.matchers.shouldBe
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode

import no.nav.sokos.skattekort.api.model.ForespoerselRequest
import no.nav.sokos.skattekort.config.ApiError
import no.nav.sokos.skattekort.forespoersel.ForespoerselRepository
import no.nav.sokos.skattekort.forespoersel.Forsystem
import no.nav.sokos.skattekort.listener.DbListener
import no.nav.sokos.skattekort.listener.MQListener
import no.nav.sokos.skattekort.listener.WiremockListener
import no.nav.sokos.skattekort.util.SQLUtils.transaction
import no.nav.sokos.skattekort.utils.ApiTestUtils.validationReport
import no.nav.sokos.skattekort.utils.TestUtils
import no.nav.sokos.skattekort.utils.TestUtils.oboTokenWithNavIdent

@OptIn(ExperimentalTime::class)
class SkattekortApiTest :
    FunSpec({
        extensions(DbListener, MQListener, WiremockListener)

        val validator =
            OpenApiInteractionValidator
                .createForSpecificationUrl("openapi/skattekort-v1-swagger.yaml")
                .build()

        val inntektsaar = Year.now().value

        test("bestille skattekort skal returnere 201 Created") {
            // Må ha withConstantNow pga. hvis denne testen kjører fra 15.12 til 31.12, så vil det bli 2 bestillinger
            withConstantNow(LocalDateTime.parse("2025-04-12T00:00:00")) {
                val fnr = "01010112345"
                WiremockListener.wiremockPDLStub(WiremockListener.generatePDLResponse(fnr))

                TestUtils.withFullTestApplication {
                    val request = ForespoerselRequest(personIdent = fnr, aar = inntektsaar, forsystem = Forsystem.MANUELL.value)
                    val response =
                        client.post("$BASE_PATH_SKATTEKORT/bestille") {
                            header(HttpHeaders.ContentType, ContentType.Application.Json)
                            header(HttpHeaders.Authorization, "Bearer $oboTokenWithNavIdent")
                            setBody(request)
                        }
                    val validationReport = response.validationReport(validator, HttpMethod.Post, "$BASE_PATH_SKATTEKORT/bestille", Json.encodeToString(request))

                    validationReport.hasErrors() shouldBe false
                    response.status shouldBe HttpStatusCode.Created

                    DbListener.dataSource.transaction { tx ->
                        ForespoerselRepository.getAllForespoersel(tx).size shouldBe 1
                    }
                }
            }
        }

        test("bestille skattekort skal returnere 400 Ugyldig request med feil personIdent") {
            TestUtils.withFullTestApplication {

                val request = ForespoerselRequest(personIdent = "1234567", aar = inntektsaar, forsystem = Forsystem.MANUELL.value)
                val response =
                    client.post("$BASE_PATH_SKATTEKORT/bestille") {
                        header(HttpHeaders.ContentType, ContentType.Application.Json)
                        header(HttpHeaders.Authorization, "Bearer $oboTokenWithNavIdent")
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

                DbListener.dataSource.transaction { tx ->
                    ForespoerselRepository.getAllForespoersel(tx) shouldBe emptyList()
                }
            }
        }

        test("bestille skattekort skal returnere 400 Ugyldig request med feil aar") {
            TestUtils.withFullTestApplication {
                val inntekstaar = Year.now().minusYears(2).value

                val request = ForespoerselRequest(personIdent = "01010112345", aar = inntekstaar, forsystem = Forsystem.MANUELL.value)
                val response =
                    client.post("$BASE_PATH_SKATTEKORT/bestille") {
                        header(HttpHeaders.ContentType, ContentType.Application.Json)
                        header(HttpHeaders.Authorization, "Bearer $oboTokenWithNavIdent")
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

                DbListener.dataSource.transaction { tx ->
                    ForespoerselRepository.getAllForespoersel(tx) shouldBe emptyList()
                }
            }
        }

        test("bestille skattekort skal returnere 400 Ugyldig request med feil forsystem") {
            TestUtils.withFullTestApplication {
                val request = ForespoerselRequest(personIdent = "01010112345", aar = inntektsaar, forsystem = "")
                val response =
                    client.post("$BASE_PATH_SKATTEKORT/bestille") {
                        header(HttpHeaders.ContentType, ContentType.Application.Json)
                        header(HttpHeaders.Authorization, "Bearer $oboTokenWithNavIdent")
                        setBody(request)
                    }

                val validationReport = response.validationReport(validator, HttpMethod.Post, "$BASE_PATH_SKATTEKORT/bestille", Json.encodeToString(request))

                validationReport.hasErrors() shouldBe false
                response.status shouldBe HttpStatusCode.BadRequest

                val apiError = response.body<ApiError>()
                apiError.error shouldBe HttpStatusCode.BadRequest.description
                apiError.status shouldBe HttpStatusCode.BadRequest.value
                apiError.message shouldBe "forsystem er ugyldig. Gyldige verdier er: OS, MANUELL"
                apiError.path shouldBe "$BASE_PATH_SKATTEKORT/bestille"

                DbListener.dataSource.transaction { tx ->
                    ForespoerselRepository.getAllForespoersel(tx) shouldBe emptyList()
                }
            }
        }
    })
