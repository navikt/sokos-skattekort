package no.nav.sokos.skattekort.api

import java.time.Year

import kotlin.time.ExperimentalTime
import kotlinx.serialization.json.Json

import com.atlassian.oai.validator.OpenApiInteractionValidator
import com.atlassian.oai.validator.model.SimpleRequest
import com.atlassian.oai.validator.model.SimpleResponse
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode

import no.nav.sokos.skattekort.TestUtil
import no.nav.sokos.skattekort.TestUtil.readFile
import no.nav.sokos.skattekort.config.ApiError
import no.nav.sokos.skattekort.infrastructure.DbListener
import no.nav.sokos.skattekort.infrastructure.MQListener
import no.nav.sokos.skattekort.module.forespoersel.ForespoerselRepository
import no.nav.sokos.skattekort.module.forespoersel.Forsystem
import no.nav.sokos.skattekort.util.SQLUtils.transaction

@OptIn(ExperimentalTime::class)
class SkattekortApiTest :
    FunSpec({
        extensions(DbListener, MQListener)

        val validator =
            OpenApiInteractionValidator
                .createForSpecificationUrl("openapi/skattekort-v1-swagger.yaml")
                .build()

        val tokenWithNavIdent = readFile("/tokenWithNavIdent.txt")
        val aar = Year.now().value

        test("bestille skattekort skal returnere 201 Created") {
            TestUtil.withFullTestApplication {
                val request = ForespoerselRequest(personIdent = "12345678901", aar = aar, forsystem = Forsystem.MANUELL.value)

                val response =
                    client.post("$BASE_PATH/bestille") {
                        header(HttpHeaders.ContentType, ContentType.Application.Json)
                        header(HttpHeaders.Authorization, "Bearer $tokenWithNavIdent")
                        setBody(request)
                    }

                val validationReport =
                    validator.validate(
                        SimpleRequest
                            .Builder("POST", "$BASE_PATH/bestille")
                            .withHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                            .withHeader(HttpHeaders.Authorization, "Bearer $tokenWithNavIdent")
                            .withBody(Json.encodeToString(request))
                            .build(),
                        SimpleResponse
                            .Builder(response.status.value)
                            .build(),
                    )
                validationReport.hasErrors() shouldBe false
                response.status shouldBe HttpStatusCode.Created

                DbListener.dataSource.transaction { tx ->
                    ForespoerselRepository.getAllForespoersel(tx).size shouldBe 1
                }
            }
        }

        test("bestille skattekort skal returnere 400 Ugyldig request med feil personIdent") {
            TestUtil.withFullTestApplication {

                val request = ForespoerselRequest(personIdent = "1234567", aar = aar, forsystem = Forsystem.MANUELL.value)

                val response =
                    client.post("$BASE_PATH/bestille") {
                        header(HttpHeaders.ContentType, ContentType.Application.Json)
                        header(HttpHeaders.Authorization, "Bearer $tokenWithNavIdent")
                        setBody(request)
                    }

                val validationReport =
                    validator.validate(
                        SimpleRequest
                            .Builder("POST", "$BASE_PATH/bestille")
                            .withHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                            .withHeader(HttpHeaders.Authorization, "Bearer $tokenWithNavIdent")
                            .withBody(Json.encodeToString(request))
                            .build(),
                        SimpleResponse
                            .Builder(response.status.value)
                            .withBody(response.bodyAsText())
                            .build(),
                    )

                validationReport.hasErrors() shouldBe false
                response.status shouldBe HttpStatusCode.BadRequest

                val apiError = response.body<ApiError>()
                apiError.error shouldBe HttpStatusCode.BadRequest.description
                apiError.status shouldBe HttpStatusCode.BadRequest.value
                apiError.message shouldBe "personIdent er ugyldig. Tillatt format er 11 siffer"
                apiError.path shouldBe "$BASE_PATH/bestille"

                DbListener.dataSource.transaction { tx ->
                    ForespoerselRepository.getAllForespoersel(tx) shouldBe emptyList()
                }
            }
        }

        test("bestille skattekort skal returnere 400 Ugyldig request med feil aar") {
            TestUtil.withFullTestApplication {
                val aar = Year.now().minusYears(2).value
                val request = ForespoerselRequest(personIdent = "12345678901", aar = aar, forsystem = Forsystem.MANUELL.value)

                val response =
                    client.post("$BASE_PATH/bestille") {
                        header(HttpHeaders.ContentType, ContentType.Application.Json)
                        header(HttpHeaders.Authorization, "Bearer $tokenWithNavIdent")
                        setBody(request)
                    }

                val validationReport =
                    validator.validate(
                        SimpleRequest
                            .Builder("POST", "$BASE_PATH/bestille")
                            .withHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                            .withHeader(HttpHeaders.Authorization, "Bearer $tokenWithNavIdent")
                            .withBody(Json.encodeToString(request))
                            .build(),
                        SimpleResponse
                            .Builder(response.status.value)
                            .withBody(response.bodyAsText())
                            .build(),
                    )

                validationReport.hasErrors() shouldBe false
                response.status shouldBe HttpStatusCode.BadRequest

                val apiError = response.body<ApiError>()
                apiError.error shouldBe HttpStatusCode.BadRequest.description
                apiError.status shouldBe HttpStatusCode.BadRequest.value
                apiError.message shouldBe "Gyldig årstall er mellom ${Year.now().minusYears(1)} og inneværende år"
                apiError.path shouldBe "$BASE_PATH/bestille"

                DbListener.dataSource.transaction { tx ->
                    ForespoerselRepository.getAllForespoersel(tx) shouldBe emptyList()
                }
            }
        }

        test("bestille skattekort skal returnere 400 Ugyldig request med feil forsystem") {
            TestUtil.withFullTestApplication {
                val request = ForespoerselRequest(personIdent = "12345678901", aar = aar, forsystem = "")

                val response =
                    client.post("$BASE_PATH/bestille") {
                        header(HttpHeaders.ContentType, ContentType.Application.Json)
                        header(HttpHeaders.Authorization, "Bearer $tokenWithNavIdent")
                        setBody(request)
                    }

                val validationReport =
                    validator.validate(
                        SimpleRequest
                            .Builder("POST", "$BASE_PATH/bestille")
                            .withHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                            .withHeader(HttpHeaders.Authorization, "Bearer $tokenWithNavIdent")
                            .withBody(Json.encodeToString(request))
                            .build(),
                        SimpleResponse
                            .Builder(response.status.value)
                            .withBody(response.bodyAsText())
                            .build(),
                    )

                validationReport.hasErrors() shouldBe false
                response.status shouldBe HttpStatusCode.BadRequest

                val apiError = response.body<ApiError>()
                apiError.error shouldBe HttpStatusCode.BadRequest.description
                apiError.status shouldBe HttpStatusCode.BadRequest.value
                apiError.message shouldBe "forsystem er ugyldig. Gyldige verdier er: OS, MANUELL"
                apiError.path shouldBe "$BASE_PATH/bestille"

                DbListener.dataSource.transaction { tx ->
                    ForespoerselRepository.getAllForespoersel(tx) shouldBe emptyList()
                }
            }
        }
    })
