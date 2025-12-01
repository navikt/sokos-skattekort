package no.nav.sokos.skattekort.module.skattekortpersonapi.v1

import java.time.LocalDateTime

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.atlassian.oai.validator.restassured.OpenApiValidationFilter
import com.nimbusds.jwt.SignedJWT
import io.kotest.assertions.json.shouldEqualJson
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.time.withConstantNow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldMatch
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.netty.handler.codec.http.HttpHeaderValues.APPLICATION_JSON
import io.restassured.RestAssured
import io.restassured.response.Response
import io.restassured.specification.RequestSpecification
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.containsString
import org.slf4j.LoggerFactory

import no.nav.sokos.skattekort.infrastructure.DbListener
import no.nav.sokos.skattekort.infrastructure.FullNettyApplication
import no.nav.sokos.skattekort.infrastructure.MQListener
import no.nav.sokos.skattekort.security.JWT_CLAIM_NAVIDENT

class SkattekortpersonApiE2ETest :
    FunSpec({
        beforeTest {
            try {
                FullNettyApplication.start()
            } catch (t: Throwable) {
                println("Ouchie! " + t)
            }
        }

        extensions(DbListener, MQListener)

        val tokenWithNavIdent: SignedJWT =
            FullNettyApplication.oauthServer.issueToken(
                issuerId = "default",
                claims =
                    mapOf(JWT_CLAIM_NAVIDENT to "aUser"),
            )
        val openApiValidationFilter = OpenApiValidationFilter("openapi/sokos-skattekort-person-v1-swagger.yaml")

        fun client(): RequestSpecification =
            RestAssured
                .given()
                .filter(openApiValidationFilter)
                .header(HttpHeaders.ContentType, APPLICATION_JSON.toString())
                .header(HttpHeaders.Authorization, "Bearer ${tokenWithNavIdent.serialize()}")
                .port(FullNettyApplication.PORT)

        test("for kort fnr dør på seg") {
            withConstantNow(LocalDateTime.parse("2025-04-12T00:00:00")) {
                DbListener.loadDataSet("database/skattekort/person_med_skattekort.sql")

                val response: Response =
                    client()
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

                assertThat("Vi får en sane feilmelding", response.body().prettyPrint(), containsString("fnr er ugyldig. Tillatt format er 11 siffer, var 1"))
            }
        }
        test("fnr med bokstaver dør på seg") {
            withConstantNow(LocalDateTime.parse("2025-04-12T00:00:00")) {
                DbListener.loadDataSet("database/skattekort/person_med_skattekort.sql")

                val response: Response =
                    client()
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

                assertThat("Vi får en sane feilmelding", response.body().prettyPrint(), containsString("fnr er ugyldig. Tillatt format er 11 siffer, var a2345678901"))
            }
        }
        test("veldig stort inntektsaar dør på seg") {
            withConstantNow(LocalDateTime.parse("2025-04-12T00:00:00")) {
                DbListener.loadDataSet("database/skattekort/person_med_skattekort.sql")

                val response: Response =
                    client()
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
                assertThat("Vi får en sane feilmelding", response.body().prettyPrint(), containsString("Gyldig årstall er mellom 2024 og inneværende år, var 20252"))
            }
        }
        test("vi kan hente et prosent-skattekort") {
            withConstantNow(LocalDateTime.parse("2025-04-12T00:00:00")) {
                DbListener.loadDataSet("database/skattekort/person_med_skattekort.sql")
                val listAppender = ListAppender<ILoggingEvent>()
                val auditLogger: Logger =
                    LoggerFactory
                        .getLogger("auditLogger") as Logger
                listAppender.start()
                auditLogger.addAppender(listAppender)
                try {
                    val response: Response =
                        client()
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
      "utstedtDato": "2025-11-11",
      "skattekortidentifikator": 17,
      "forskuddstrekk": [
        {
          "type": "Trekkprosent",
          "trekkode": "PENSJON_FRA_NAV",
          "prosentsats": 18.50,
          "antallMaanederForTrekk": 12.0
        }
      ]
    },
    "tilleggsopplysning": [
    ]
  }
]""",
                    )
                    listAppender.list.size shouldBe 1
                    listAppender.list.get(0).formattedMessage shouldMatch
                        "CEF\\:0\\|Utbetalingsportalen\\|sokos\\-skattekort\\|1\\.0\\|audit\\:access\\|sokos\\-skattekort\\|INFO\\|suid\\=aUser duid\\=12345678901 end=\\d+ msg\\=NAV\\-ansatt har søkt etter skattekort for bruker"
                } finally {
                    auditLogger.detachAppender(listAppender)
                    listAppender.stop()
                }
            }
        }
        test("vi kan hente et frikort med beløpsgrense") {
            withConstantNow(LocalDateTime.parse("2025-04-12T00:00:00")) {
                DbListener.loadDataSet("database/skattekort/person_med_skattekort.sql")

                val response: Response =
                    client()
                        .body(
                            """{
                            | "fnr": "12345678902",
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
    "arbeidstakeridentifikator": "12345678902",
    "resultatPaaForespoersel": "skattekortopplysningerOK",
    "skattekort": {
      "utstedtDato": "2025-11-11",
      "skattekortidentifikator": 18,
      "forskuddstrekk": [
        {
          "type": "Frikort",
          "trekkode": "LOENN_FRA_NAV",
          "frikortbeloep": 65000
        }
      ]
    },
    "tilleggsopplysning": []
  }
]""",
                )
            }
        }
    })
