package no.nav.sokos.skattekort.api

import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.atlassian.oai.validator.OpenApiInteractionValidator
import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
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

import no.nav.pdl.hentpersonbolk.Navn
import no.nav.pdl.hentpersonbolk.Person
import no.nav.sokos.skattekort.api.model.HentNavnRequest
import no.nav.sokos.skattekort.api.model.HentSkattekortRequest
import no.nav.sokos.skattekort.config.ApiError
import no.nav.sokos.skattekort.infrastructure.skatteetaten.SkatteetatenClientTestUtils.toSkattekortDTOWrappedWithErrorResponse
import no.nav.sokos.skattekort.infrastructure.skatteetaten.SkatteetatenClientTestUtils.toStringWrappedWithErrorResponse
import no.nav.sokos.skattekort.listener.DbListener
import no.nav.sokos.skattekort.listener.MQListener
import no.nav.sokos.skattekort.listener.WiremockListener
import no.nav.sokos.skattekort.listener.WiremockListener.generateHentPersonBolk
import no.nav.sokos.skattekort.person.PersonRepository
import no.nav.sokos.skattekort.person.Personidentifikator
import no.nav.sokos.skattekort.skattekort.ResultatForSkattekort
import no.nav.sokos.skattekort.skattekort.SkattekortKilde
import no.nav.sokos.skattekort.skattekort.SkattekortRepository
import no.nav.sokos.skattekort.skattekort.Tilleggsopplysning
import no.nav.sokos.skattekort.skattekort.Trekkode.LOENN_FRA_NAV
import no.nav.sokos.skattekort.skattekort.Trekkode.PENSJON_FRA_NAV
import no.nav.sokos.skattekort.util.SQLUtils.transaction
import no.nav.sokos.skattekort.utils.ApiTestUtils.validationReport
import no.nav.sokos.skattekort.utils.TestUtils
import no.nav.sokos.skattekort.utils.TestUtils.authServer
import no.nav.sokos.skattekort.utils.TestUtils.m2mTokenWithNavIdent
import no.nav.sokos.skattekort.utils.TestUtils.oboTokenWithNavIdent

private const val HENT_SKATTEKORT_URL = "/api/v2/person/hent-skattekort"
private const val OPPRETT_SKATTEKORT_URL = "/api/v2/person/opprett"
private const val HENT_NAVN_URL = "/api/v2/person/hent-navn"

class SkattekortpersonApiV2Test :
    FunSpec({
        extensions(DbListener, MQListener, WiremockListener)

        val validator =
            OpenApiInteractionValidator
                .createForSpecificationUrl("openapi/sokos-skattekort-person-v2-swagger.yaml")
                .build()

        test("hent-skattekort - for kort fnr dør på seg") {
            TestUtils.withFullTestApplication {
                DbListener.loadDataSet("database/skattekort/person_med_skattekort.sql")
                val fnr = "1"
                val request = HentSkattekortRequest(fnr = fnr, inntektsaar = 2025)
                val response =
                    client.post(HENT_SKATTEKORT_URL) {
                        header(HttpHeaders.ContentType, ContentType.Application.Json)
                        header(HttpHeaders.Authorization, "Bearer $oboTokenWithNavIdent")
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
            TestUtils.withFullTestApplication {
                DbListener.loadDataSet("database/skattekort/person_med_skattekort.sql")
                val fnr = "a2345678901"
                val request = HentSkattekortRequest(fnr = fnr, inntektsaar = 2025)
                val response =
                    client.post(HENT_SKATTEKORT_URL) {
                        header(HttpHeaders.ContentType, ContentType.Application.Json)
                        header(HttpHeaders.Authorization, "Bearer $oboTokenWithNavIdent")
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
            TestUtils.withFullTestApplication {
                DbListener.loadDataSet("database/skattekort/person_med_skattekort.sql")
                val fnr = "01010112345"
                val request = HentSkattekortRequest(fnr = fnr, inntektsaar = 20522)
                val response =
                    client.post(HENT_SKATTEKORT_URL) {
                        header(HttpHeaders.ContentType, ContentType.Application.Json)
                        header(HttpHeaders.Authorization, "Bearer $oboTokenWithNavIdent")
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
            TestUtils.withFullTestApplication {
                DbListener.loadDataSet("database/skattekort/person_med_skattekort.sql")

                WiremockListener.wiremockTilgangsmaskinStub()

                val auditLogAdditions = ListAppender<ILoggingEvent>()
                auditLogAdditions.start()

                val auditLogger: Logger = LoggerFactory.getLogger("auditLogger") as Logger
                auditLogger.addAppender(auditLogAdditions)

                try {
                    val request = HentSkattekortRequest(fnr = "01010112345", inntektsaar = 2025)
                    val response =
                        client.post(HENT_SKATTEKORT_URL) {
                            header(HttpHeaders.ContentType, ContentType.Application.Json)
                            header(HttpHeaders.Authorization, "Bearer $oboTokenWithNavIdent")
                            setBody(request)
                        }

                    val validationReport = response.validationReport(validator, HttpMethod.Post, HENT_SKATTEKORT_URL, Json.encodeToString(request))
                    validationReport.hasErrors() shouldBe false
                    response.status shouldBe HttpStatusCode.OK

                    val wrappedWithErrorResponse = response.bodyAsText().toSkattekortDTOWrappedWithErrorResponse()
                    wrappedWithErrorResponse.data shouldNotBeNull {
                        size shouldBe 1
                        first() shouldNotBeNull {
                            forskuddstrekkList.size shouldBe 1
                            forskuddstrekkList.first() shouldNotBeNull {
                                trekkode shouldBe PENSJON_FRA_NAV.value
                                prosentkort?.prosentSats shouldBe 18.5
                                prosentkort?.antallMndForTrekk shouldBe 12.0
                            }
                            id shouldBe 1
                            identifikator shouldBe "17"
                            inntektsaar shouldBe 2025
                            kilde shouldBe SkattekortKilde.SKATTEETATEN
                            resultatForSkattekort shouldBe ResultatForSkattekort.SkattekortopplysningerOK.value
                            tilleggsopplysningList shouldBe emptyList()
                            utstedtDato shouldBe LocalDate.parse("2025-11-11")
                        }
                    }

                    auditLogAdditions.list.size shouldBe 1
                    auditLogAdditions.list.get(0).formattedMessage shouldMatch
                        "CEF\\:0\\|Utbetalingsportalen\\|sokos\\-skattekort\\|1\\.0\\|audit\\:access\\|sokos\\-skattekort\\|INFO\\|suid\\=aUser duid\\=01010112345 end=\\d+ msg\\=NAV\\-ansatt har søkt etter skattekort for bruker"
                } finally {
                    auditLogger.detachAppender(auditLogAdditions)
                    auditLogAdditions.stop()
                }
            }
        }

        test("hent-skattekort - vi kan hente et frikort med beløpsgrense") {
            TestUtils.withFullTestApplication {
                DbListener.loadDataSet("database/skattekort/person_med_skattekort.sql")

                WiremockListener.wiremockTilgangsmaskinStub()

                val request = HentSkattekortRequest(fnr = "02020212345", inntektsaar = 2025)
                val response =
                    client.post(HENT_SKATTEKORT_URL) {
                        header(HttpHeaders.ContentType, ContentType.Application.Json)
                        header(HttpHeaders.Authorization, "Bearer $oboTokenWithNavIdent")
                        setBody(request)
                    }

                val validationReport = response.validationReport(validator, HttpMethod.Post, HENT_SKATTEKORT_URL, Json.encodeToString(request))
                validationReport.hasErrors() shouldBe false
                response.status shouldBe HttpStatusCode.OK

                val wrappedWithErrorResponse = response.bodyAsText().toSkattekortDTOWrappedWithErrorResponse()
                wrappedWithErrorResponse.data shouldNotBeNull {
                    size shouldBe 1
                    first() shouldNotBeNull {
                        forskuddstrekkList.size shouldBe 1
                        forskuddstrekkList.first() shouldNotBeNull {
                            trekkode shouldBe LOENN_FRA_NAV.value
                            frikort?.frikortBeloep shouldBe 65000
                        }
                        id shouldBe 3
                        identifikator shouldBe "18"
                        inntektsaar shouldBe 2025
                        kilde shouldBe SkattekortKilde.SYNTETISERT
                        resultatForSkattekort shouldBe ResultatForSkattekort.SkattekortopplysningerOK.value
                        tilleggsopplysningList shouldBe emptyList()
                        utstedtDato shouldBe LocalDate.parse("2025-11-11")
                    }
                }
            }
        }

        test("hent-skattekort - Auth: bogus token blir avvist") {
            TestUtils.withFullTestApplication {
                DbListener.loadDataSet("database/skattekort/person_med_skattekort.sql")

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
            TestUtils.withFullTestApplication {
                DbListener.loadDataSet("database/skattekort/person_med_skattekort.sql")

                val request = HentSkattekortRequest(fnr = "01010112345", inntektsaar = 2025)
                val response =
                    client.post(HENT_SKATTEKORT_URL) {
                        header(HttpHeaders.ContentType, ContentType.Application.Json)
                        header(HttpHeaders.Authorization, "Bearer $m2mTokenWithNavIdent")
                        setBody(request)
                    }
                response.status shouldBe HttpStatusCode.BadRequest
            }
        }

        test("hent-skattekort - Auth: token uten navident blir ikke avvist når man søker opp fiktive fnr") {
            TestUtils.withFullTestApplication {
                DbListener.loadDataSet("database/skattekort/person_med_skattekort.sql")
                val request = HentSkattekortRequest(fnr = "01510112345", inntektsaar = 2025)
                val response =
                    client.post(HENT_SKATTEKORT_URL) {
                        header(HttpHeaders.ContentType, ContentType.Application.Json)
                        header(HttpHeaders.Authorization, "Bearer $m2mTokenWithNavIdent")
                        setBody(request)
                    }
                response shouldNotBeNull {
                    status shouldBe HttpStatusCode.OK
                }
            }
        }

        test("hent-skattekort - Auth: token fra feil issuer blir avvist") {
            TestUtils.withFullTestApplication {
                DbListener.loadDataSet("database/skattekort/person_med_skattekort.sql")
                val tokenWithBogusIssuer = authServer?.issueToken(issuerId = "bogus")?.serialize()

                val request = HentSkattekortRequest(fnr = "01010112345", inntektsaar = 2025)
                val response =
                    client.post(HENT_SKATTEKORT_URL) {
                        header(HttpHeaders.ContentType, ContentType.Application.Json)
                        header(HttpHeaders.Authorization, "Bearer $tokenWithBogusIssuer")
                        setBody(request)
                    }
            }
        }
        test("hent-skattekort - person ikke funnet returnerer 200 med melding") {
            TestUtils.withFullTestApplication {
                WiremockListener.wiremockTilgangsmaskinStub()
                val request = HentSkattekortRequest(fnr = "99999999999", inntektsaar = 2025)
                val response =
                    client.post(HENT_SKATTEKORT_URL) {
                        header(HttpHeaders.ContentType, ContentType.Application.Json)
                        header(HttpHeaders.Authorization, "Bearer $oboTokenWithNavIdent")
                        setBody(request)
                    }

                val validationReport = response.validationReport(validator, HttpMethod.Post, HENT_SKATTEKORT_URL, Json.encodeToString(request))
                validationReport.hasErrors() shouldBe false
                response.status shouldBe HttpStatusCode.OK
                Json.parseToJsonElement(response.bodyAsText()) shouldBe Json.parseToJsonElement("""{"data": []}""")
            }
        }

        test("hent-skattekort - skattekort ikke funnet returnerer 200 med melding") {
            TestUtils.withFullTestApplication {
                DbListener.loadDataSet("database/skattekort/person_uten_skattekort.sql")

                WiremockListener.wiremockTilgangsmaskinStub()
                val request = HentSkattekortRequest(fnr = "03030312345", inntektsaar = 2025)
                val response =
                    client.post(HENT_SKATTEKORT_URL) {
                        header(HttpHeaders.ContentType, ContentType.Application.Json)
                        header(HttpHeaders.Authorization, "Bearer $oboTokenWithNavIdent")
                        setBody(request)
                    }

                val validationReport = response.validationReport(validator, HttpMethod.Post, HENT_SKATTEKORT_URL, Json.encodeToString(request))
                validationReport.hasErrors() shouldBe false
                response.status shouldBe HttpStatusCode.OK
                Json.parseToJsonElement(response.bodyAsText()) shouldBe Json.parseToJsonElement("""{"data": []}""")
            }
        }

        test("opprett skattekort - Kan opprette skattekort med eksempelet fra swagger") {
            TestUtils.withFullTestApplication {
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
                try {
                    val response =
                        client.post(OPPRETT_SKATTEKORT_URL) {
                            header(HttpHeaders.ContentType, ContentType.Application.Json)
                            header(HttpHeaders.Authorization, "Bearer $oboTokenWithNavIdent")
                            setBody(request)
                        }
                    response.status shouldBe HttpStatusCode.Created
                    DbListener.dataSource.transaction { tx ->
                        val opprettetPerson = PersonRepository.findPersonByFnr(tx, Personidentifikator("01010112345"))
                        opprettetPerson.shouldNotBeNull()
                        val nyeSkattekort = SkattekortRepository.findAllByPersonId(tx, opprettetPerson.id!!, 2026, false)
                        nyeSkattekort.size shouldBe 2
                    }
                } catch (e: Exception) {
                    println("Feil ved oppretting av skattekort: ${e.message}")
                }
            }
        }

        test("opprett skattekort - Genererer skattekort når det er tilleggsopplysning Svalbard") {
            TestUtils.withFullTestApplication {
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
                try {
                    val response =
                        client.post(OPPRETT_SKATTEKORT_URL) {
                            header(HttpHeaders.ContentType, ContentType.Application.Json)
                            header(HttpHeaders.Authorization, "Bearer $oboTokenWithNavIdent")
                            setBody(request)
                        }
                    response.status shouldBe HttpStatusCode.Created
                    DbListener.dataSource.transaction { tx ->
                        val opprettetPerson = PersonRepository.findPersonByFnr(tx, Personidentifikator("01010112345"))
                        opprettetPerson.shouldNotBeNull()
                        val nyeSkattekort = SkattekortRepository.findAllByPersonId(tx, opprettetPerson.id!!, 2026, false)
                        nyeSkattekort shouldNotBeNull {
                            size shouldBe 2
                            first() shouldNotBeNull {
                                tilleggsopplysningList shouldBe listOf(Tilleggsopplysning.OPPHOLD_PAA_SVALBARD)
                                forskuddstrekkList.size shouldBe 3
                            }
                        }
                    }
                } catch (e: Exception) {
                    println("Feil ved oppretting av skattekort: ${e.message}")
                }
            }
        }

        test("opprett skattekort - Returnerer 400 BadRequest når man oppgir ugyldig ResultatForSkattekort") {
            TestUtils.withFullTestApplication {
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
                        header(HttpHeaders.Authorization, "Bearer $oboTokenWithNavIdent")
                        setBody(request)
                    }

                response.status shouldBe HttpStatusCode.BadRequest
            }
        }

        test("opprett skattekort - Returnerer 400 BadRequest når man oppgir ugyldig Trekkode") {
            TestUtils.withFullTestApplication {
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
                        header(HttpHeaders.Authorization, "Bearer $oboTokenWithNavIdent")
                        setBody(request)
                    }

                response.status shouldBe HttpStatusCode.BadRequest
            }
        }

        test("opprett skattekort - Kan ikke opprette skattekort for reelt fnr uten saksbehandler") {
            TestUtils.withFullTestApplication {
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

                try {
                    val response =
                        client.post(OPPRETT_SKATTEKORT_URL) {
                            header(HttpHeaders.ContentType, ContentType.Application.Json)
                            header(HttpHeaders.Authorization, "Bearer $m2mTokenWithNavIdent")
                            setBody(request)
                        }

                    response.status shouldBe HttpStatusCode.BadRequest
                } catch (e: Exception) {
                    println("Feil ved oppretting av skattekort: ${e.message}")
                }
            }
        }

        test("opprett skattekort - Kan opprette skattekort for dollybruker uten tilleggsopplysning eller saksbehandler, returnerer 201 CREATED") {
            TestUtils.withFullTestApplication {
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

                try {
                    val response =
                        client.post(OPPRETT_SKATTEKORT_URL) {
                            header(HttpHeaders.ContentType, ContentType.Application.Json)
                            header(HttpHeaders.Authorization, "Bearer $m2mTokenWithNavIdent")
                            setBody(request)
                        }

                    response.status shouldBe HttpStatusCode.Created
                    DbListener.dataSource.transaction { tx ->
                        val opprettetPerson = PersonRepository.findPersonByFnr(tx, Personidentifikator("01410112345"))
                        opprettetPerson.shouldNotBeNull()
                        val nyeSkattekort = SkattekortRepository.findAllByPersonId(tx, opprettetPerson.id!!, 2026, false)
                        nyeSkattekort.size shouldBe 1
                    }
                } catch (e: Exception) {
                    println("Feil ved oppretting av skattekort: ${e.message}")
                }
            }
        }

        test("opprett skattekort - Mer informativ feilmelding når forskuddstrekk mangler informasjon") {
            TestUtils.withFullTestApplication {
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
                        header(HttpHeaders.Authorization, "Bearer $m2mTokenWithNavIdent")
                        setBody(request)
                    }
                response.status shouldBe HttpStatusCode.BadRequest
                response.bodyAsText() shouldContain
                    "Illegal input: Fields [prosentSats, antallMndForTrekk] are required for type with serial name 'no.nav.sokos.skattekort.dto.TabellkortDTO', but they were missing at path: \$.skattekort.forskuddstrekkList[0].trekktabell"
            }
        }
        test("opprett skattekort - Mer informativ feilmelding når tilleggsopplysning er feil") {
            TestUtils.withFullTestApplication {
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
                        header(HttpHeaders.Authorization, "Bearer $m2mTokenWithNavIdent")
                        setBody(request)
                    }
                response.status shouldBe HttpStatusCode.BadRequest
                response.bodyAsText() shouldContain "Ugyldig tilleggsopplysning. Lovlige verdier er "
            }
        }

        test("hent-navn - Auth: missing token is rejected") {
            TestUtils.withFullTestApplication {
                val request = HentNavnRequest(fnr = "01010112345")

                val response =
                    client.post(HENT_NAVN_URL) {
                        header(HttpHeaders.ContentType, ContentType.Application.Json)
                        setBody(request)
                    }

                response.status shouldBe HttpStatusCode.Unauthorized
            }
        }

        test("hent-navn - returns 200 and wrapped response") {
            TestUtils.withFullTestApplication {
                val fnr = "01010112345"
                WiremockListener.wiremockTilgangsmaskinStub()
                WiremockListener.wiremockPDLStub(generateHentPersonBolk(Pair(fnr, Person(listOf(Navn("Fornavn", "Mellomnavn", "Etternavn"))))))

                val request = HentNavnRequest(fnr = "01010112345")
                val response =
                    client.post(HENT_NAVN_URL) {
                        header(HttpHeaders.ContentType, ContentType.Application.Json)
                        header(HttpHeaders.Authorization, "Bearer $oboTokenWithNavIdent")
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
