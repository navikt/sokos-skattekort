package no.nav.sokos.skattekort.api

import kotlinx.serialization.json.Json

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.atlassian.oai.validator.OpenApiInteractionValidator
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldMatch
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import org.slf4j.LoggerFactory

import no.nav.sokos.skattekort.api.skattekortpersonapi.v1.SkattekortPersonRequest
import no.nav.sokos.skattekort.config.ApiError
import no.nav.sokos.skattekort.infrastructure.DbListener
import no.nav.sokos.skattekort.infrastructure.MQListener
import no.nav.sokos.skattekort.utils.TestUtils
import no.nav.sokos.skattekort.utils.TestUtils.authServer
import no.nav.sokos.skattekort.utils.TestUtils.readFile
import no.nav.sokos.skattekort.utils.TestUtils.tokenWithNavIdent
import no.nav.sokos.skattekort.utils.validationReport

private const val HENT_SKATTEKORT_URL = "/api/v1/hent-skattekort"

class SkattekortpersonApiE2ETest :
    FunSpec({
        extensions(DbListener, MQListener)

        val validator =
            OpenApiInteractionValidator
                .createForSpecificationUrl("openapi/sokos-skattekort-person-v1-swagger.yaml")
                .build()

        test("for kort fnr dør på seg") {
            TestUtils.withFullTestApplication {
                DbListener.loadDataSet("database/skattekort/person_med_skattekort.sql")
                val fnr = "1"
                val request = SkattekortPersonRequest(fnr = fnr, inntektsaar = 2025)
                val response =
                    client.post(HENT_SKATTEKORT_URL) {
                        header(HttpHeaders.ContentType, ContentType.Application.Json)
                        header(HttpHeaders.Authorization, "Bearer $tokenWithNavIdent")
                        setBody(request)
                    }

                val validationReport = response.validationReport(validator, HttpMethod.Post, HENT_SKATTEKORT_URL, Json.encodeToString(request))
                validationReport.hasErrors() shouldBe false
                response.status shouldBe HttpStatusCode.BadRequest

                val apiError = response.body<ApiError>()
                apiError.error shouldBe HttpStatusCode.BadRequest.description
                apiError.status shouldBe HttpStatusCode.BadRequest.value
                apiError.message shouldBe "fnr er ugyldig. Tillatt format er 11 siffer, var $fnr"
                apiError.path shouldBe HENT_SKATTEKORT_URL
            }
        }

        test("fnr med bokstaver dør på seg") {
            TestUtils.withFullTestApplication {
                DbListener.loadDataSet("database/skattekort/person_med_skattekort.sql")
                val fnr = "a2345678901"
                val request = SkattekortPersonRequest(fnr = fnr, inntektsaar = 2025)
                val response =
                    client.post(HENT_SKATTEKORT_URL) {
                        header(HttpHeaders.ContentType, ContentType.Application.Json)
                        header(HttpHeaders.Authorization, "Bearer $tokenWithNavIdent")
                        setBody(request)
                    }

                val validationReport = response.validationReport(validator, HttpMethod.Post, HENT_SKATTEKORT_URL, Json.encodeToString(request))
                validationReport.hasErrors() shouldBe false
                response.status shouldBe HttpStatusCode.BadRequest

                val apiError = response.body<ApiError>()
                apiError.error shouldBe HttpStatusCode.BadRequest.description
                apiError.status shouldBe HttpStatusCode.BadRequest.value
                apiError.message shouldBe "fnr er ugyldig. Tillatt format er 11 siffer, var $fnr"
                apiError.path shouldBe HENT_SKATTEKORT_URL
            }
        }

        test("veldig stort inntektsaar dør på seg") {
            TestUtils.withFullTestApplication {
                DbListener.loadDataSet("database/skattekort/person_med_skattekort.sql")
                val fnr = "12345678901"
                val request = SkattekortPersonRequest(fnr = fnr, inntektsaar = 20522)
                val response =
                    client.post(HENT_SKATTEKORT_URL) {
                        header(HttpHeaders.ContentType, ContentType.Application.Json)
                        header(HttpHeaders.Authorization, "Bearer $tokenWithNavIdent")
                        setBody(request)
                    }

                val validationReport = response.validationReport(validator, HttpMethod.Post, HENT_SKATTEKORT_URL, Json.encodeToString(request))
                validationReport.hasErrors() shouldBe false
                response.status shouldBe HttpStatusCode.BadRequest

                val apiError = response.body<ApiError>()
                apiError.error shouldBe HttpStatusCode.BadRequest.description
                apiError.status shouldBe HttpStatusCode.BadRequest.value
                apiError.message shouldBe "inntektsaar ser ikke ut som et gyldig årstall, var 20522"
                apiError.path shouldBe HENT_SKATTEKORT_URL
            }
        }

        test("vi kan hente et prosent-skattekort") {
            TestUtils.withFullTestApplication {
                DbListener.loadDataSet("database/skattekort/person_med_skattekort.sql")

                val listAppender = ListAppender<ILoggingEvent>()
                listAppender.start()

                val auditLogger: Logger = LoggerFactory.getLogger("auditLogger") as Logger
                auditLogger.addAppender(listAppender)

                try {
                    val request = SkattekortPersonRequest(fnr = "12345678901", inntektsaar = 2025)
                    val response =
                        client.post(HENT_SKATTEKORT_URL) {
                            header(HttpHeaders.ContentType, ContentType.Application.Json)
                            header(HttpHeaders.Authorization, "Bearer $tokenWithNavIdent")
                            setBody(request)
                        }

                    val validationReport = response.validationReport(validator, HttpMethod.Post, HENT_SKATTEKORT_URL, Json.encodeToString(request))
                    validationReport.hasErrors() shouldBe false
                    response.status shouldBe HttpStatusCode.OK

                    Json.parseToJsonElement(response.bodyAsText()) shouldBe Json.parseToJsonElement(readFile("/api/skattekortPensjonFraNav.json"))

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
            TestUtils.withFullTestApplication {
                DbListener.loadDataSet("database/skattekort/person_med_skattekort.sql")

                val request = SkattekortPersonRequest(fnr = "12345678902", inntektsaar = 2025)
                val response =
                    client.post(HENT_SKATTEKORT_URL) {
                        header(HttpHeaders.ContentType, ContentType.Application.Json)
                        header(HttpHeaders.Authorization, "Bearer $tokenWithNavIdent")
                        setBody(request)
                    }

                val validationReport = response.validationReport(validator, HttpMethod.Post, HENT_SKATTEKORT_URL, Json.encodeToString(request))
                validationReport.hasErrors() shouldBe false
                response.status shouldBe HttpStatusCode.OK

                Json.parseToJsonElement(response.bodyAsText()) shouldBe Json.parseToJsonElement(readFile("/api/skattekortFrikortLoennFraNav.json"))
            }
        }

        test("Auth: bogus token blir avvist") {
            TestUtils.withFullTestApplication {
                DbListener.loadDataSet("database/skattekort/person_med_skattekort.sql")

                val request = SkattekortPersonRequest(fnr = "12345678901", inntektsaar = 2025)
                val response =
                    client.post(HENT_SKATTEKORT_URL) {
                        header(HttpHeaders.ContentType, ContentType.Application.Json)
                        setBody(request)
                    }
                response.status shouldBe HttpStatusCode.Unauthorized
            }
        }

        test("Auth: token uten navident blir avvist") {
            TestUtils.withFullTestApplication {
                DbListener.loadDataSet("database/skattekort/person_med_skattekort.sql")
                val tokenWithoutNavIdent = authServer?.issueToken(issuerId = "default")?.serialize()

                tokenWithoutNavIdent shouldNotBe null

                val request = SkattekortPersonRequest(fnr = "12345678901", inntektsaar = 2025)
                val response =
                    client.post(HENT_SKATTEKORT_URL) {
                        header(HttpHeaders.ContentType, ContentType.Application.Json)
                        header(HttpHeaders.Authorization, "Bearer $tokenWithoutNavIdent")
                        setBody(request)
                    }
                response.status shouldBe HttpStatusCode.Unauthorized
            }
        }

        test("Auth: token fra feil issuer blir avvist") {
            TestUtils.withFullTestApplication {
                DbListener.loadDataSet("database/skattekort/person_med_skattekort.sql")
                val tokenWithoutNavIdent = authServer?.issueToken(issuerId = "bogus")?.serialize()

                val request = SkattekortPersonRequest(fnr = "12345678901", inntektsaar = 2025)
                val response =
                    client.post(HENT_SKATTEKORT_URL) {
                        header(HttpHeaders.ContentType, ContentType.Application.Json)
                        header(HttpHeaders.Authorization, "Bearer $tokenWithoutNavIdent")
                        setBody(request)
                    }
            }
        }
        test("person ikke funnet returnerer 200 med melding") {
            TestUtils.withFullTestApplication {
                val request = SkattekortPersonRequest(fnr = "99999999999", inntektsaar = 2025)
                val response =
                    client.post(HENT_SKATTEKORT_URL) {
                        header(HttpHeaders.ContentType, ContentType.Application.Json)
                        header(HttpHeaders.Authorization, "Bearer $tokenWithNavIdent")
                        setBody(request)
                    }

                val validationReport = response.validationReport(validator, HttpMethod.Post, HENT_SKATTEKORT_URL, Json.encodeToString(request))
                validationReport.hasErrors() shouldBe false
                response.status shouldBe HttpStatusCode.OK
                Json.parseToJsonElement(response.bodyAsText()) shouldBe Json.parseToJsonElement("""[]""")
            }
        }

        test("skattekort ikke funnet returnerer 200 med melding") {
            TestUtils.withFullTestApplication {
                DbListener.loadDataSet("database/skattekort/person_uten_skattekort.sql")
                val request = SkattekortPersonRequest(fnr = "12345678903", inntektsaar = 2025)
                val response =
                    client.post(HENT_SKATTEKORT_URL) {
                        header(HttpHeaders.ContentType, ContentType.Application.Json)
                        header(HttpHeaders.Authorization, "Bearer $tokenWithNavIdent")
                        setBody(request)
                    }

                val validationReport = response.validationReport(validator, HttpMethod.Post, HENT_SKATTEKORT_URL, Json.encodeToString(request))
                validationReport.hasErrors() shouldBe false
                response.status shouldBe HttpStatusCode.OK
                Json.parseToJsonElement(response.bodyAsText()) shouldBe Json.parseToJsonElement("""[]""")
            }
        }
    })
