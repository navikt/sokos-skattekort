package no.nav.sokos.skattekort.api

import kotlinx.serialization.json.Json

import com.atlassian.oai.validator.OpenApiInteractionValidator
import io.github.resilience4j.core.functions.Either
import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk

import no.nav.sokos.skattekort.api.model.HentSkattekortRequest
import no.nav.sokos.skattekort.config.ApiError
import no.nav.sokos.skattekort.infrastructure.pdl.PdlService
import no.nav.sokos.skattekort.skattekort.SkattekortService
import no.nav.sokos.skattekort.utils.ApiTestUtils.validationReport
import no.nav.sokos.skattekort.utils.m2mToken
import no.nav.sokos.skattekort.utils.oboToken
import no.nav.sokos.skattekort.utils.withApiTestApplication

private val skattekortService: SkattekortService = mockk()
private val pdlService: PdlService = mockk()

private const val HENT_SKATTEKORT_URL = "/api/v1/person/hent-skattekort"
private const val OPPRETT_SKATTEKORT_URL = "/api/v1/person/opprett"

@Deprecated("Denne klassen tester både hent og opprett, og bør splittes i SkattekortpersonApiV1HentTest og SkattekortpersonApiV1OpprettTest for å gjøre det tydeligere hva som testes i hver test")
class SkattekortpersonApiV1Test :
    FunSpec({
        val validator =
            OpenApiInteractionValidator
                .createForSpecificationUrl("openapi/sokos-skattekort-person-v1-swagger.yaml")
                .build()

        beforeEach {
            clearMocks(skattekortService, pdlService)
        }

        test("for kort fnr dør på seg") {
            withApiTestApplication(
                routeSetup = { skattekortPersonApi(skattekortService, pdlService) },
            ) { authServer, client ->
                val fnr = "1"
                val request = HentSkattekortRequest(fnr = fnr, inntektsaar = 2025)
                val response =
                    client.post(HENT_SKATTEKORT_URL) {
                        header(HttpHeaders.ContentType, ContentType.Application.Json)
                        header(HttpHeaders.Authorization, "Bearer ${authServer.oboToken()}")
                        setBody(request)
                    }

                val validationReport = response.validationReport(validator, HttpMethod.Post, HENT_SKATTEKORT_URL, Json.encodeToString(request))
                val apiError = response.body<ApiError>()
                assertSoftly {
                    validationReport.hasErrors() shouldBe true
                    response.status shouldBe HttpStatusCode.BadRequest
                    apiError.error shouldBe HttpStatusCode.BadRequest.description
                    apiError.status shouldBe HttpStatusCode.BadRequest.value
                    apiError.message shouldBe "fnr er ugyldig. Tillatt format er 11 siffer, var $fnr"
                    apiError.path shouldBe HENT_SKATTEKORT_URL
                }
            }
        }

        test("fnr med bokstaver dør på seg") {
            withApiTestApplication(
                routeSetup = { skattekortPersonApi(skattekortService, pdlService) },
            ) { authServer, client ->
                val fnr = "a2345678901"
                val request = HentSkattekortRequest(fnr = fnr, inntektsaar = 2025)
                val response =
                    client.post(HENT_SKATTEKORT_URL) {
                        header(HttpHeaders.ContentType, ContentType.Application.Json)
                        header(HttpHeaders.Authorization, "Bearer ${authServer.oboToken()}")
                        setBody(request)
                    }

                val validationReport = response.validationReport(validator, HttpMethod.Post, HENT_SKATTEKORT_URL, Json.encodeToString(request))
                val apiError = response.body<ApiError>()
                assertSoftly {
                    validationReport.hasErrors() shouldBe true
                    response.status shouldBe HttpStatusCode.BadRequest
                    apiError.error shouldBe HttpStatusCode.BadRequest.description
                    apiError.status shouldBe HttpStatusCode.BadRequest.value
                    apiError.message shouldBe "fnr er ugyldig. Tillatt format er 11 siffer, var $fnr"
                    apiError.path shouldBe HENT_SKATTEKORT_URL
                }
            }
        }

        test("veldig stort inntektsaar dør på seg") {
            withApiTestApplication(
                routeSetup = { skattekortPersonApi(skattekortService, pdlService) },
            ) { authServer, client ->
                val fnr = "01010112345"
                val request = HentSkattekortRequest(fnr = fnr, inntektsaar = 20522)
                val response =
                    client.post(HENT_SKATTEKORT_URL) {
                        header(HttpHeaders.ContentType, ContentType.Application.Json)
                        header(HttpHeaders.Authorization, "Bearer ${authServer.oboToken()}")
                        setBody(request)
                    }

                val swaggerValidationReport = response.validationReport(validator, HttpMethod.Post, HENT_SKATTEKORT_URL, Json.encodeToString(request))
                swaggerValidationReport.hasErrors() shouldBe true
                response.status shouldBe HttpStatusCode.BadRequest

                val apiError = response.body<ApiError>()
                apiError.error shouldBe HttpStatusCode.BadRequest.description
                apiError.status shouldBe HttpStatusCode.BadRequest.value
                apiError.message shouldBe "inntektsaar ser ikke ut som et gyldig årstall, var 20522"
                apiError.path shouldBe HENT_SKATTEKORT_URL
            }
        }

        test("vi kan hente et prosent-skattekort") {
            withApiTestApplication(
                routeSetup = { skattekortPersonApi(skattekortService, pdlService) },
            ) { authServer, client ->
                coEvery { skattekortService.getSingleSkattekortForEachYear(any(), any(), any()) } returns Either.right(emptyList())

                val request = HentSkattekortRequest(fnr = "01010112345", inntektsaar = 2025)
                val response =
                    client.post(HENT_SKATTEKORT_URL) {
                        header(HttpHeaders.ContentType, ContentType.Application.Json)
                        header(HttpHeaders.Authorization, "Bearer ${authServer.oboToken()}")
                        setBody(request)
                    }

                val validationReport = response.validationReport(validator, HttpMethod.Post, HENT_SKATTEKORT_URL, Json.encodeToString(request))
                validationReport.hasErrors() shouldBe false
                response.status shouldBe HttpStatusCode.OK
            }
        }

        test("vi kan hente et frikort med beløpsgrense") {
            withApiTestApplication(
                routeSetup = { skattekortPersonApi(skattekortService, pdlService) },
            ) { authServer, client ->
                coEvery { skattekortService.getSingleSkattekortForEachYear(any(), any(), any()) } returns Either.right(emptyList())

                val request = HentSkattekortRequest(fnr = "02020212345", inntektsaar = 2025)
                val response =
                    client.post(HENT_SKATTEKORT_URL) {
                        header(HttpHeaders.ContentType, ContentType.Application.Json)
                        header(HttpHeaders.Authorization, "Bearer ${authServer.oboToken()}")
                        setBody(request)
                    }

                val validationReport = response.validationReport(validator, HttpMethod.Post, HENT_SKATTEKORT_URL, Json.encodeToString(request))
                validationReport.hasErrors() shouldBe false
                response.status shouldBe HttpStatusCode.OK
            }
        }

        test("Auth: bogus token blir avvist") {
            withApiTestApplication(
                routeSetup = { skattekortPersonApi(skattekortService, pdlService) },
            ) { _, client ->
                val request = HentSkattekortRequest(fnr = "01010112345", inntektsaar = 2025)
                val response =
                    client.post(HENT_SKATTEKORT_URL) {
                        header(HttpHeaders.ContentType, ContentType.Application.Json)
                        setBody(request)
                    }
                response.status shouldBe HttpStatusCode.Unauthorized
            }
        }

        test("Auth: token uten navident blir avvist pga reelt fnr") {
            withApiTestApplication(
                routeSetup = { skattekortPersonApi(skattekortService, pdlService) },
            ) { authServer, client ->
                coEvery { skattekortService.getSingleSkattekortForEachYear(any(), any(), any()) } throws
                    IllegalArgumentException("Oppslag på reelle skattekort må gjøres på vegne av en saksbehandler")

                val request = HentSkattekortRequest(fnr = "01010112345", inntektsaar = 2025)
                val response =
                    client.post(HENT_SKATTEKORT_URL) {
                        header(HttpHeaders.ContentType, ContentType.Application.Json)
                        header(HttpHeaders.Authorization, "Bearer ${authServer.m2mToken()}")
                        setBody(request)
                    }
                response.status shouldBe HttpStatusCode.BadRequest
            }
        }

        test("Auth: token uten navident blir ikke avvist når man søker opp fiktive fnr") {
            withApiTestApplication(
                routeSetup = { skattekortPersonApi(skattekortService, pdlService) },
            ) { authServer, client ->
                coEvery { skattekortService.getSingleSkattekortForEachYear(any(), any(), any()) } returns Either.right(emptyList())

                val request = HentSkattekortRequest(fnr = "01510112345", inntektsaar = 2025)
                val response =
                    client.post(HENT_SKATTEKORT_URL) {
                        header(HttpHeaders.ContentType, ContentType.Application.Json)
                        header(HttpHeaders.Authorization, "Bearer ${authServer.m2mToken()}")
                        setBody(request)
                    }
                response.status shouldBe HttpStatusCode.OK
            }
        }

        test("Auth: token fra feil issuer blir avvist") {
            withApiTestApplication(
                routeSetup = { skattekortPersonApi(skattekortService, pdlService) },
            ) { authServer, client ->
                val tokenWithBogusIssuer = authServer.issueToken(issuerId = "bogus").serialize()

                val request = HentSkattekortRequest(fnr = "01010112345", inntektsaar = 2025)
                val response =
                    client.post(HENT_SKATTEKORT_URL) {
                        header(HttpHeaders.ContentType, ContentType.Application.Json)
                        header(HttpHeaders.Authorization, "Bearer $tokenWithBogusIssuer")
                        setBody(request)
                    }
                val validationReport = response.validationReport(validator, HttpMethod.Post, HENT_SKATTEKORT_URL, Json.encodeToString(request))
                validationReport.hasErrors() shouldBe false
                response.status shouldBe HttpStatusCode.Unauthorized
            }
        }

        test("person ikke funnet returnerer 200 med melding") {
            withApiTestApplication(
                routeSetup = { skattekortPersonApi(skattekortService, pdlService) },
            ) { authServer, client ->
                coEvery { skattekortService.getSingleSkattekortForEachYear(any(), any(), any()) } returns Either.right(emptyList())

                val request = HentSkattekortRequest(fnr = "99999999999", inntektsaar = 2025)
                val response =
                    client.post(HENT_SKATTEKORT_URL) {
                        header(HttpHeaders.ContentType, ContentType.Application.Json)
                        header(HttpHeaders.Authorization, "Bearer ${authServer.oboToken()}")
                        setBody(request)
                    }

                val validationReport = response.validationReport(validator, HttpMethod.Post, HENT_SKATTEKORT_URL, Json.encodeToString(request))
                validationReport.hasErrors() shouldBe false
                response.status shouldBe HttpStatusCode.OK
                Json.parseToJsonElement(response.bodyAsText()) shouldBe Json.parseToJsonElement("""[]""")
            }
        }

        test("skattekort ikke funnet returnerer 200 med melding") {
            withApiTestApplication(
                routeSetup = { skattekortPersonApi(skattekortService, pdlService) },
            ) { authServer, client ->
                coEvery { skattekortService.getSingleSkattekortForEachYear(any(), any(), any()) } returns Either.right(emptyList())

                val request = HentSkattekortRequest(fnr = "03030312345", inntektsaar = 2025)
                val response =
                    client.post(HENT_SKATTEKORT_URL) {
                        header(HttpHeaders.ContentType, ContentType.Application.Json)
                        header(HttpHeaders.Authorization, "Bearer ${authServer.oboToken()}")
                        setBody(request)
                    }

                val validationReport = response.validationReport(validator, HttpMethod.Post, HENT_SKATTEKORT_URL, Json.encodeToString(request))
                validationReport.hasErrors() shouldBe false
                response.status shouldBe HttpStatusCode.OK
                Json.parseToJsonElement(response.bodyAsText()) shouldBe Json.parseToJsonElement("""[]""")
            }
        }

        test("Kan opprette skattekort med eksempelet fra swagger") {
            withApiTestApplication(
                routeSetup = { skattekortPersonApi(skattekortService, pdlService) },
            ) { authServer, client ->
                every { skattekortService.createManualSkattekort(any(), any(), any()) } returns 1L

                val request =
                    """
                    {
                      "fnr": "01010112345",
                      "skattekort": {
                        "utstedtDato": "2026-01-22",
                        "inntektsaar": 2026,
                        "resultatForSkattekort": "skattekortopplysningerOK",
                        "forskuddstrekkList": [
                          {
                            "trekkode": "loennFraNAV",
                            "trekktabell": 
                            {
                              "tabell": "8010",
                              "prosentSats": 25.5,
                              "antallMndForTrekk": 10.5
                            }
                          }
                        ],
                        "tilleggsopplysningList": [
                          "oppholdPaaSvalbard"
                        ]
                      }
                    }
                    """.trimIndent()
                val response =
                    client.post(OPPRETT_SKATTEKORT_URL) {
                        header(HttpHeaders.ContentType, ContentType.Application.Json)
                        header(HttpHeaders.Authorization, "Bearer ${authServer.oboToken()}")
                        setBody(request)
                    }
                response.status shouldBe HttpStatusCode.Created
            }
        }

        test("Genererer skattekort når det er tilleggsopplysning Svalbard") {
            withApiTestApplication(
                routeSetup = { skattekortPersonApi(skattekortService, pdlService) },
            ) { authServer, client ->
                every { skattekortService.createManualSkattekort(any(), any(), any()) } returns 1L

                val request =
                    """
                    {
                      "fnr": "01010112345",
                      "skattekort": {
                        "utstedtDato": "2026-01-22",
                        "inntektsaar": 2026,
                        "resultatForSkattekort": "ikkeSkattekort",
                        "forskuddstrekkList": [],
                        "tilleggsopplysningList": [
                          "oppholdPaaSvalbard"
                        ]
                      }
                    }
                    """.trimIndent()
                val response =
                    client.post(OPPRETT_SKATTEKORT_URL) {
                        header(HttpHeaders.ContentType, ContentType.Application.Json)
                        header(HttpHeaders.Authorization, "Bearer ${authServer.oboToken()}")
                        setBody(request)
                    }
                response.status shouldBe HttpStatusCode.Created
            }
        }

        test("Returnerer 400 BadRequest når man oppgir ugyldig ResultatForSkattekort") {
            withApiTestApplication(
                routeSetup = { skattekortPersonApi(skattekortService, pdlService) },
            ) { authServer, client ->
                val request =
                    """
                    {
                      "fnr": "01010112345",
                      "skattekort": {
                        "utstedtDato": "2026-01-22",
                        "inntektsaar": 2026,
                        "resultatForSkattekort": "ugyldigVerdi",
                        "forskuddstrekkList": [
                          {
                            "trekkode": "loennFraNAV",
                            "tabell": "8010",
                            "prosentSats": 25.5,
                            "antallMndForTrekk": 10.5
                          }
                        ]
                      }
                    }
                    """.trimIndent()

                val response =
                    client.post(OPPRETT_SKATTEKORT_URL) {
                        header(HttpHeaders.ContentType, ContentType.Application.Json)
                        header(HttpHeaders.Authorization, "Bearer ${authServer.oboToken()}")
                        setBody(request)
                    }

                response.status shouldBe HttpStatusCode.BadRequest
            }
        }

        test("Returnerer 400 BadRequest når man oppgir ugyldig Trekkode") {
            withApiTestApplication(
                routeSetup = { skattekortPersonApi(skattekortService, pdlService) },
            ) { authServer, client ->
                val request =
                    """
                    {
                      "fnr": "01010112345",
                      "skattekort": {
                        "utstedtDato": "2026-01-22",
                        "inntektsaar": 2026,
                        "forskuddstrekkList": [
                          {
                            "trekkode": "ugyldigTrekkode",
                            "tabell": "8010",
                            "prosentSats": 25.5,
                            "antallMndForTrekk": 10.5
                          }
                        ]
                      }
                    }
                    """.trimIndent()

                val response =
                    client.post(OPPRETT_SKATTEKORT_URL) {
                        header(HttpHeaders.ContentType, ContentType.Application.Json)
                        header(HttpHeaders.Authorization, "Bearer ${authServer.oboToken()}")
                        setBody(request)
                    }

                response.status shouldBe HttpStatusCode.BadRequest
            }
        }

        test("Kan ikke opprette skattekort for reelt fnr uten saksbehandler") {
            withApiTestApplication(
                routeSetup = { skattekortPersonApi(skattekortService, pdlService) },
            ) { authServer, client ->
                every { skattekortService.createManualSkattekort(any(), any(), any()) } throws
                    IllegalArgumentException("Manuell opprettelse av reelle skattekort må gjøres på vegne av en saksbehandler")

                val request =
                    """
                    {    
                        "fnr" : "01010112345",
                        "skattekort": {
                            "utstedtDato": "2026-01-22",
                            "inntektsaar": 2026,
                            "resultatForSkattekort": "skattekortopplysningerOK",
                            "forskuddstrekkList": [
                                 {
                                    "trekkode": "loennFraNAV",
                                    "tabell": "8010",
                                    "prosentSats": 25.5,
                                    "antallMndForTrekk": 10.5
                                 }
                            ]
                        }
                    }
                    """.trimIndent()

                val response =
                    client.post(OPPRETT_SKATTEKORT_URL) {
                        header(HttpHeaders.ContentType, ContentType.Application.Json)
                        header(HttpHeaders.Authorization, "Bearer ${authServer.m2mToken()}")
                        setBody(request)
                    }

                response.status shouldBe HttpStatusCode.BadRequest
            }
        }

        test("Kan opprette skattekort for dollybruker uten tilleggsopplysning eller saksbehandler, returnerer 201 CREATED") {
            withApiTestApplication(
                routeSetup = { skattekortPersonApi(skattekortService, pdlService) },
            ) { authServer, client ->
                every { skattekortService.createManualSkattekort(any(), any(), any()) } returns 1L

                val request =
                    """
                    {
                        "fnr": "01410112345",
                        "skattekort": {
                            "utstedtDato": "2026-01-22",
                            "inntektsaar": 2026,
                            "resultatForSkattekort": "skattekortopplysningerOK",
                            "forskuddstrekkList": [
                                 {
                                    "trekkode": "loennFraNAV",
                                    "trekktabell": {
                                       "tabell": "8010",
                                       "prosentSats": 25.5,
                                       "antallMndForTrekk": 10.5
                                    }
                                 }
                            ]
                        }
                    }
                    """.trimIndent()

                val response =
                    client.post(OPPRETT_SKATTEKORT_URL) {
                        header(HttpHeaders.ContentType, ContentType.Application.Json)
                        header(HttpHeaders.Authorization, "Bearer ${authServer.m2mToken()}")
                        setBody(request)
                    }

                response.status shouldBe HttpStatusCode.Created
            }
        }

        test("Mer informativ feilmelding når forskuddstrekk mangler informasjon") {
            withApiTestApplication(
                routeSetup = { skattekortPersonApi(skattekortService, pdlService) },
            ) { authServer, client ->
                val request =
                    """
                    {
                      "fnr" : "01410112345",
                      "skattekort" : {
                        "inntektsaar" : 2026,
                        "resultatForSkattekort" : "skattekortopplysningerOK",
                        "forskuddstrekkList" : [ {
                          "trekkode" : "loennFraNAV",
                          "trekktabell" : {
                            "tabell" : ""
                          }
                        } ],
                        "tilleggsopplysningList" : [ ]
                      }
                    }
                    """.trimIndent()

                val response =
                    client.post(OPPRETT_SKATTEKORT_URL) {
                        header(HttpHeaders.ContentType, ContentType.Application.Json)
                        header(HttpHeaders.Authorization, "Bearer ${authServer.m2mToken()}")
                        setBody(request)
                    }
                response.status shouldBe HttpStatusCode.BadRequest
                response.bodyAsText() shouldContain
                    "Illegal input: Fields [prosentSats, antallMndForTrekk] are required for type with serial name 'no.nav.sokos.skattekort.api.model.v1.TabellkortDTO', but they were missing at path: \$.skattekort.forskuddstrekkList[0].trekktabell"
            }
        }

        test("Mer informativ feilmelding når tilleggsopplysning er feil") {
            withApiTestApplication(
                routeSetup = { skattekortPersonApi(skattekortService, pdlService) },
            ) { authServer, client ->
                val request =
                    """
                    {
                      "fnr" : "01410112345",
                      "skattekort" : {
                        "inntektsaar" : 2026,
                        "resultatForSkattekort" : "skattekortopplysningerOK",
                        "forskuddstrekkList" : [ {
                          "trekkode" : "loennFraNAV",
                          "trekktabell" : {
                            "tabell" : "1234",
                            "prosentSats" : 25.5,
                            "antallMndForTrekk" : 10.5
                          }
                        } ],
                        "tilleggsopplysningList" : [ "kildeskattPaaLoenn" ]
                      }
                    }
                    """.trimIndent()

                val response =
                    client.post(OPPRETT_SKATTEKORT_URL) {
                        header(HttpHeaders.ContentType, ContentType.Application.Json)
                        header(HttpHeaders.Authorization, "Bearer ${authServer.m2mToken()}")
                        setBody(request)
                    }
                response.status shouldBe HttpStatusCode.BadRequest
                response.bodyAsText() shouldContain "Ugyldig tilleggsopplysning. Lovlige verdier er "
            }
        }
    })
