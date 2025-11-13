package no.nav.sokos.skattekort.module.skattekortpersonapi.v1

import java.time.LocalDateTime

import com.atlassian.oai.validator.restassured.OpenApiValidationFilter
import io.kotest.assertions.json.shouldEqualJson
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.time.withConstantNow
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.mockk.mockk
import io.netty.handler.codec.http.HttpHeaderValues.APPLICATION_JSON
import io.restassured.RestAssured
import io.restassured.response.Response
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.containsString

import no.nav.sokos.skattekort.TestUtil.readFile
import no.nav.sokos.skattekort.TestUtil.withFullTestApplication
import no.nav.sokos.skattekort.infrastructure.DbListener
import no.nav.sokos.skattekort.infrastructure.FullNettyApplication
import no.nav.sokos.skattekort.infrastructure.MQListener
import no.nav.sokos.skattekort.infrastructure.SftpListener
import no.nav.sokos.skattekort.module.forespoersel.ForespoerselService

private val forespoerselService = mockk<ForespoerselService>()

class SkattekortpersonApiE2ETest :
    FunSpec({
        beforeTest {
            FullNettyApplication.start()
        }

        extensions(DbListener, MQListener, SftpListener)

        val tokenWithNavIdent = readFile("/tokenWithNavIdent.txt")
        val openApiValidationFilter = OpenApiValidationFilter("openapi/sokos-skattekort-person-v1-swagger.yaml")
        val client =
            RestAssured
                .given()
                .filter(openApiValidationFilter)
                .header(HttpHeaders.ContentType, APPLICATION_JSON.toString())
                .header(HttpHeaders.Authorization, "Bearer $tokenWithNavIdent")
                .port(FullNettyApplication.PORT)

        test("for kort fnr dør på seg") {
            withConstantNow(LocalDateTime.parse("2025-04-12T00:00:00")) {
                DbListener.loadDataSet("database/skattekort/person_med_skattekort.sql")

                val response: Response =
                    client
                        .body(
                            """{
                        | "fnr": "1",
                        | "inntektsaar": 2025
                        | }
                            """.trimMargin(),
                        ).post("/api/v1/hent-skattekort")
                        .then()
                        .assertThat()
                        .statusCode(HttpStatusCode.BadRequest.value)
                        .extract()
                        .response()!!

                assertThat("Vi får en sane feilmelding", response.body().prettyPrint(), containsString("fnr må ha lengde 11, var 1"))
            }
        }
        test("fnr med bokstaver dør på seg") {
            withConstantNow(LocalDateTime.parse("2025-04-12T00:00:00")) {
                withFullTestApplication {
                    DbListener.loadDataSet("database/skattekort/person_med_skattekort.sql")

                    val response: Response =
                        client
                            .body(
                                """{
                            | "fnr": "a2345678901",
                            | "inntektsaar": 2025
                            | }
                                """.trimMargin(),
                            ).post("/api/v1/hent-skattekort")
                            .then()
                            .assertThat()
                            .statusCode(HttpStatusCode.BadRequest.value)
                            .extract()
                            .response()!!

                    assertThat("Vi får en sane feilmelding", response.body().prettyPrint(), containsString("fnr kan bare inneholde siffer, var a2345678901"))
                }
            }
        }
        test("veldig stort inntektsaar dør på seg") {
            withConstantNow(LocalDateTime.parse("2025-04-12T00:00:00")) {
                withFullTestApplication {
                    DbListener.loadDataSet("database/skattekort/person_med_skattekort.sql")

                    val response: Response =
                        client
                            .body(
                                """{
                            | "fnr": "12345678901",
                            | "inntektsaar": 20252
                            | }
                                """.trimMargin(),
                            ).post("/api/v1/hent-skattekort")
                            .then()
                            .assertThat()
                            .statusCode(HttpStatusCode.BadRequest.value)
                            .extract()
                            .response()!!
                    assertThat("Vi får en sane feilmelding", response.body().prettyPrint(), containsString("inntektsaar ser ikke ut som et gyldig årstall, var 20252"))
                }
            }
        }
        test("vi kan hente et skattekort") {
            withConstantNow(LocalDateTime.parse("2025-04-12T00:00:00")) {
                withFullTestApplication {
                    DbListener.loadDataSet("database/skattekort/person_med_skattekort.sql")

                    val response: Response =
                        client
                            .body(
                                """{
                            | "fnr": "12345678901",
                            | "inntektsaar": 2025
                            | }
                                """.trimMargin(),
                            ).post("/api/v1/hent-skattekort")
                            .then()
                            .assertThat()
                            .statusCode(HttpStatusCode.OK.value)
                            .extract()
                            .response()!!
                    response.body().prettyPrint().shouldEqualJson(
                        """[
  {
    "inntektsaar": 2025,
    "arbeidstakeridentifikator": "12345678901",
    "resultatPaaForespoersel": "skattekortopplysningerOK",
    "skattekort": {
      "utstedtDato": "2025-01-06",
      "skattekortidentifikator": 17,
      "forskuddstrekk": [
        {
          "type": "Trekktabell",
          "trekkode": "LOENN_FRA_HOVEDARBEIDSGIVER",
          "tabellnummer": "7100",
          "prosentsats": 27.50,
          "antallMaanederForTrekk": 12.0
        }
      ]
    },
    "tilleggsopplysning": [
      "kildeskattPaaLoenn"
    ]
  }
]""",
                    )
                }
            }
        }
    })
