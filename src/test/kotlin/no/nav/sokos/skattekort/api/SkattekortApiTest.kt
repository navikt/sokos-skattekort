package no.nav.sokos.skattekort.api

import java.time.Year

import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.serialization.json.Json

import com.atlassian.oai.validator.restassured.OpenApiValidationFilter
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import io.ktor.server.routing.routing
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.netty.handler.codec.http.HttpHeaderValues.APPLICATION_JSON
import io.restassured.RestAssured

import no.nav.sokos.skattekort.TestUtil.readFile
import no.nav.sokos.skattekort.config.AUTHENTICATION_NAME
import no.nav.sokos.skattekort.config.ApiError
import no.nav.sokos.skattekort.config.authenticate
import no.nav.sokos.skattekort.config.commonConfig
import no.nav.sokos.skattekort.module.forespoersel.ForespoerselService
import no.nav.sokos.skattekort.module.forespoersel.Forsystem

private const val PORT = 9090
private val forespoerselService = mockk<ForespoerselService>()

@OptIn(ExperimentalTime::class)
class SkattekortApiTest :
    FunSpec({
        lateinit var server: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>

        val tokenWithNavIdent = readFile("/tokenWithNavIdent.txt")
        val openApiValidationFilter = OpenApiValidationFilter("openapi/skattekort-v1-swagger.yaml")

        beforeTest {
            server = embeddedServer(Netty, PORT, module = Application::applicationTestModule).start()
        }

        afterTest {
            server.stop(5, 5)
        }

        afterEach {
            clearAllMocks()
        }

        test("bestille skattekort skal returnere 201 Created") {
            coEvery { forespoerselService.taImotForespoersel(message = any(), saksbehandler = any()) } returns Unit
            val aar = Year.now().value

            RestAssured
                .given()
                .filter(openApiValidationFilter)
                .header(HttpHeaders.ContentType, APPLICATION_JSON.toString())
                .header(HttpHeaders.Authorization, "Bearer $tokenWithNavIdent")
                .port(PORT)
                .body(ForespoerselRequest(personIdent = "12345678901", aar = aar, forsystem = Forsystem.MANUELL.value))
                .post("$BASE_PATH/bestille")
                .then()
                .assertThat()
                .statusCode(HttpStatusCode.Created.value)
                .extract()
                .response()

            coVerify { forespoerselService.taImotForespoersel(message = "${Forsystem.MANUELL.value};$aar;12345678901", saksbehandler = match { it.ident.isNotEmpty() }) }
        }

        test("bestille skattekort skal returnere 400 Ugyldig request med feil personIdent") {
            val aar = Year.now().value

            val response =
                RestAssured
                    .given()
                    .filter(openApiValidationFilter)
                    .header(HttpHeaders.ContentType, APPLICATION_JSON.toString())
                    .header(HttpHeaders.Authorization, "Bearer $tokenWithNavIdent")
                    .port(PORT)
                    .body(ForespoerselRequest(personIdent = "1234567", aar = aar, forsystem = Forsystem.MANUELL.value))
                    .post("$BASE_PATH/bestille")
                    .then()
                    .assertThat()
                    .statusCode(HttpStatusCode.BadRequest.value)
                    .extract()
                    .response()

            Json.decodeFromString<ApiError>(response.asString()) shouldBe
                ApiError(
                    error = HttpStatusCode.BadRequest.description,
                    status = HttpStatusCode.BadRequest.value,
                    message = "personIdent er ugyldig. Tillatt format er 11 siffer",
                    path = "$BASE_PATH/bestille",
                    timestamp = Instant.parse(response.body.jsonPath().getString("timestamp")),
                )

            coVerify(exactly = 0) { forespoerselService.taImotForespoersel(any(), any()) }
        }

        test("bestille skattekort skal returnere 400 Ugyldig request med feil aar") {
            val aar = Year.now().minusYears(2).value

            val response =
                RestAssured
                    .given()
                    .filter(openApiValidationFilter)
                    .header(HttpHeaders.ContentType, APPLICATION_JSON.toString())
                    .header(HttpHeaders.Authorization, "Bearer $tokenWithNavIdent")
                    .port(PORT)
                    .body(ForespoerselRequest(personIdent = "12345678901", aar = aar, forsystem = Forsystem.MANUELL.value))
                    .post("$BASE_PATH/bestille")
                    .then()
                    .assertThat()
                    .statusCode(HttpStatusCode.BadRequest.value)
                    .extract()
                    .response()

            Json.decodeFromString<ApiError>(response.asString()) shouldBe
                ApiError(
                    error = HttpStatusCode.BadRequest.description,
                    status = HttpStatusCode.BadRequest.value,
                    message = "Gyldig årstall er mellom 2024 og inneværende år",
                    path = "$BASE_PATH/bestille",
                    timestamp = Instant.parse(response.body.jsonPath().getString("timestamp")),
                )

            coVerify(exactly = 0) { forespoerselService.taImotForespoersel(any(), any()) }
        }

        test("bestille skattekort skal returnere 400 Ugyldig request med feil forsystem") {
            val aar = Year.now().value

            val response =
                RestAssured
                    .given()
                    .filter(openApiValidationFilter)
                    .header(HttpHeaders.ContentType, APPLICATION_JSON.toString())
                    .header(HttpHeaders.Authorization, "Bearer $tokenWithNavIdent")
                    .port(PORT)
                    .body(ForespoerselRequest(personIdent = "12345678901", aar = aar, forsystem = ""))
                    .post("$BASE_PATH/bestille")
                    .then()
                    .assertThat()
                    .statusCode(HttpStatusCode.BadRequest.value)
                    .extract()
                    .response()

            Json.decodeFromString<ApiError>(response.asString()) shouldBe
                ApiError(
                    error = HttpStatusCode.BadRequest.description,
                    status = HttpStatusCode.BadRequest.value,
                    message = "forsystem er ugyldig. Gyldige verdier er: OPPDRAGSSYSTEMET, ARENA, MANUELL",
                    path = "$BASE_PATH/bestille",
                    timestamp = Instant.parse(response.body.jsonPath().getString("timestamp")),
                )

            coVerify(exactly = 0) { forespoerselService.taImotForespoersel(any(), any()) }
        }
    })

private fun Application.applicationTestModule() {
    commonConfig()
    routing {
        authenticate(false, AUTHENTICATION_NAME) {
            skattekortApi(forespoerselService)
        }
    }
}
