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

import no.nav.sokos.skattekort.api.model.FnrRequest
import no.nav.sokos.skattekort.api.model.HentSkattekortRequest
import no.nav.sokos.skattekort.config.ApiError
import no.nav.sokos.skattekort.infrastructure.pdl.PdlService
import no.nav.sokos.skattekort.infrastructure.skatteetaten.SkatteetatenClientTestUtils.toStringWrappedWithErrorResponse
import no.nav.sokos.skattekort.skattekort.SkattekortService
import no.nav.sokos.skattekort.utils.ApiTestUtils.validationReport
import no.nav.sokos.skattekort.utils.m2mToken
import no.nav.sokos.skattekort.utils.oboToken
import no.nav.sokos.skattekort.utils.withApiTestApplication

private val skattekortService: SkattekortService = mockk()
private val pdlService: PdlService = mockk()

private const val HENT_SKATTEKORT_URL = "/api/v2/person/hent-skattekort"
private const val OPPRETT_SKATTEKORT_URL = "/api/v2/person/opprett"
private const val HENT_NAVN_URL = "/api/v2/person/hent-navn"

class SkattekortpersonApiV2Test :
    FunSpec({
        val validator =
            OpenApiInteractionValidator
                .createForSpecificationUrl("openapi/sokos-skattekort-person-v2-swagger.yaml")
                .build()

        beforeEach {
            clearMocks(skattekortService, pdlService)
        }

        test("hent-skattekort - for kort fnr dør på seg") {
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

        test("hent-skattekort - fnr med bokstaver dør på seg") {
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

        test("hent-skattekort - veldig stort inntektsaar dør på seg") {
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

        test("hent-skattekort - vi kan hente et prosent-skattekort") {
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

        test("hent-skattekort - vi kan hente et frikort med beløpsgrense") {
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

        test("hent-skattekort - Auth: bogus token blir avvist") {
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

        test("hent-skattekort - Auth: token uten navident blir avvist pga reelt fnr") {
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

        test("hent-skattekort - Auth: token uten navident blir ikke avvist når man søker opp fiktive fnr") {
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

        test("hent-skattekort - Auth: token fra feil issuer blir avvist") {
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

        test("hent-skattekort - person ikke funnet returnerer 200 med melding") {
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
                Json.parseToJsonElement(response.bodyAsText()) shouldBe Json.parseToJsonElement("""{"data": []}""")
            }
        }

        test("hent-skattekort - skattekort ikke funnet returnerer 200 med melding") {
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
                Json.parseToJsonElement(response.bodyAsText()) shouldBe Json.parseToJsonElement("""{"data": []}""")
            }
        }

        test("opprett skattekort - Kan opprette skattekort med eksempelet fra swagger") {
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

        test("opprett skattekort - Genererer skattekort når det er tilleggsopplysning Svalbard") {
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

        test("opprett skattekort - Returnerer 400 BadRequest når man oppgir ugyldig ResultatForSkattekort") {
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

        test("opprett skattekort - Returnerer 400 BadRequest når man oppgir ugyldig Trekkode") {
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

        test("opprett skattekort - Kan ikke opprette skattekort for reelt fnr uten saksbehandler") {
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

        test("opprett skattekort - Kan opprette skattekort for dollybruker uten tilleggsopplysning eller saksbehandler, returnerer 201 CREATED") {
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

        test("opprett skattekort - Mer informativ feilmelding når forskuddstrekk mangler informasjon") {
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

        test("opprett skattekort - Mer informativ feilmelding når tilleggsopplysning er feil") {
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

        test("hent-navn - Auth: missing token is rejected") {
            withApiTestApplication(
                routeSetup = { skattekortPersonApi(skattekortService, pdlService) },
            ) { _, client ->
                val request = FnrRequest(fnr = "01010112345")

                val response =
                    client.post(HENT_NAVN_URL) {
                        header(HttpHeaders.ContentType, ContentType.Application.Json)
                        setBody(request)
                    }

                response.status shouldBe HttpStatusCode.Unauthorized
            }
        }

        test("hent-navn - returns 200 and wrapped response") {
            withApiTestApplication(
                routeSetup = { skattekortPersonApi(skattekortService, pdlService) },
            ) { authServer, client ->
                coEvery { pdlService.getPersonNavn(any(), any()) } returns Either.right("Fornavn Mellomnavn Etternavn")

                val request = FnrRequest(fnr = "01010112345")
                val response =
                    client.post(HENT_NAVN_URL) {
                        header(HttpHeaders.ContentType, ContentType.Application.Json)
                        header(HttpHeaders.Authorization, "Bearer ${authServer.oboToken()}")
                        setBody(request)
                    }

                val validationReport =
                    response.validationReport(
                        validator,
                        HttpMethod.Post,
                        HENT_NAVN_URL,
                        Json.encodeToString(request),
                    )

                val wrapped = response.bodyAsText().toStringWrappedWithErrorResponse()

                assertSoftly {
                    validationReport.hasErrors() shouldBe false
                    response.status shouldBe HttpStatusCode.OK
                    wrapped.data shouldBe "Fornavn Mellomnavn Etternavn"
                }
            }
        }
    })
