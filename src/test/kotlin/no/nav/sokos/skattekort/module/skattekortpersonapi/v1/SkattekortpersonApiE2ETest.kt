package no.nav.sokos.skattekort.module.skattekortpersonapi.v1

import java.time.LocalDateTime

import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.time.withConstantNow
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.mockk.mockk
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.equalTo

import no.nav.sokos.skattekort.TestUtil.withFullTestApplication
import no.nav.sokos.skattekort.listener.DbListener
import no.nav.sokos.skattekort.listener.MQListener
import no.nav.sokos.skattekort.listener.SftpListener
import no.nav.sokos.skattekort.module.forespoersel.ForespoerselService

private const val PORT = 9090
private val forespoerselService = mockk<ForespoerselService>()

class SkattekortpersonApiE2ETest :
    FunSpec({
        extensions(DbListener, MQListener, SftpListener)
        test("for kort fnr dør på seg") {
            withConstantNow(LocalDateTime.parse("2025-04-12T00:00:00")) {
                withFullTestApplication {
                    DbListener.loadDataSet("database/skattekort/person_med_skattekort.sql")

                    val response: HttpResponse =
                        client.post("/api/hent-skattekort/1") {
                            contentType(ContentType.Application.Json)
                            setBody(
                                """{
                            | "fnr": "1",
                            | "inntektsaar": "2025"
                            | }
                                """.trimMargin(),
                            )
                        }
                    assertThat("Post returnerer ok", response.status, equalTo(HttpStatusCode.BadRequest))
                    assertThat("Vi får en sane feilmelding", response.body(), containsString("fnr må ha lengde 11, var 1"))
                }
            }
        }
        test("fnr med bokstaver dør på seg") {
            withConstantNow(LocalDateTime.parse("2025-04-12T00:00:00")) {
                withFullTestApplication {
                    DbListener.loadDataSet("database/skattekort/person_med_skattekort.sql")

                    val response: HttpResponse =
                        client.post("/api/hent-skattekort/1") {
                            contentType(ContentType.Application.Json)
                            setBody(
                                """{
                            | "fnr": "a2345678901",
                            | "inntektsaar": "2025"
                            | }
                                """.trimMargin(),
                            )
                        }
                    assertThat("Post returnerer ok", response.status, equalTo(HttpStatusCode.BadRequest))
                    assertThat("Vi får en sane feilmelding", response.body(), containsString("fnr kan bare inneholde siffer, var a2345678901"))
                }
            }
        }
        test("inntektsaar med bokstaver dør på seg") {
            withConstantNow(LocalDateTime.parse("2025-04-12T00:00:00")) {
                withFullTestApplication {
                    DbListener.loadDataSet("database/skattekort/person_med_skattekort.sql")

                    val response: HttpResponse =
                        client.post("/api/hent-skattekort/1") {
                            contentType(ContentType.Application.Json)
                            setBody(
                                """{
                            | "fnr": "12345678901",
                            | "inntektsaar": "a025"
                            | }
                                """.trimMargin(),
                            )
                        }
                    assertThat("Post returnerer ok", response.status, equalTo(HttpStatusCode.BadRequest))
                    assertThat("Vi får en sane feilmelding", response.body(), containsString("inntektsaar kan bare inneholde siffer, var a025"))
                }
            }
        }
        test("veldig stort inntektsaar dør på seg") {
            withConstantNow(LocalDateTime.parse("2025-04-12T00:00:00")) {
                withFullTestApplication {
                    DbListener.loadDataSet("database/skattekort/person_med_skattekort.sql")

                    val response: HttpResponse =
                        client.post("/api/hent-skattekort/1") {
                            contentType(ContentType.Application.Json)
                            setBody(
                                """{
                            | "fnr": "12345678901",
                            | "inntektsaar": "20252"
                            | }
                                """.trimMargin(),
                            )
                        }
                    assertThat("Post returnerer ok", response.status, equalTo(HttpStatusCode.BadRequest))
                    assertThat("Vi får en sane feilmelding", response.body(), containsString("inntektsaar ser ikke ut som et årstall, var 20252"))
                }
            }
        }
        test("vi kan hente et skattekort") {
            withConstantNow(LocalDateTime.parse("2025-04-12T00:00:00")) {
                withFullTestApplication {
                    DbListener.loadDataSet("database/skattekort/person_med_skattekort.sql")

                    val response: HttpResponse =
                        client.post("/api/hent-skattekort/1") {
                            contentType(ContentType.Application.Json)
                            setBody(
                                """{
                            | "fnr": "12345678901",
                            | "inntektsaar": "2025"
                            | }
                                """.trimMargin(),
                            )
                        }
                    assertThat("Post returnerer ok", response.status, equalTo(HttpStatusCode.OK))
                    assertThat(
                        "Vi får et skattekort",
                        response.body(),
                        equalTo(
                            """[
    {
        "inntektsaar": 2025,
        "arbeidstakeridentifikator": "12345678901",
        "resultatPaaForespoersel": "SKATTEKORTOPPLYSNINGER_OK",
        "skattekort": {
            "utstedtDato": "2025-11-02",
            "skattekortidentifikator": 17,
            "forskuddstrekk": [
                {
                    "type": "no.nav.sokos.skattekort.module.skattekortpersonapi.v1.Trekktabell",
                    "trekkode": "LOENN_FRA_HOVEDARBEIDSGIVER",
                    "tabellnummer": "7100",
                    "prosentsats": 27.50,
                    "antallMaanederForTrekk": 12.0
                }
            ]
        },
        "tilleggsopplysning": [
            "KILDESKATT_PAA_LOENN"
        ]
    }
]""",
                        ),
                    )
                }
            }
        }
    })
