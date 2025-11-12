package no.nav.sokos.skattekort.module.skattekort

import kotlinx.serialization.json.Json

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.mockk.coEvery
import io.mockk.mockk

import no.nav.sokos.skattekort.TestUtil.readFile
import no.nav.sokos.skattekort.module.person.Personidentifikator
import no.nav.sokos.skattekort.security.MaskinportenTokenClient
import no.nav.sokos.skattekort.skatteetaten.SkatteetatenClient
import no.nav.sokos.skattekort.skatteetaten.bestillskattekort.bestillSkattekortRequest

class SkatteetatenClientTest :
    FunSpec({

        test("bestillSkattekort") {
            val bestillSkattekortRequest =
                bestillSkattekortRequest(
                    2025,
                    listOf(
                        Personidentifikator("01010100001"),
                    ),
                )
            val skatteetatenClient = setupClient(readFile("/skatteetaten/bestillSkattekort/bestillSkattekortResponse.json"))

            val response = skatteetatenClient.bestillSkattekort(bestillSkattekortRequest)

            response shouldNotBeNull {
                dialogreferanse shouldBe "1"
                bestillingsreferanse shouldBe "TEST8128"
            }
        }

        test("should handle ugyldig inntektsaar") {
            val skatteetatenClient = setupClient(readFile("/skatteetaten/hentSkattekort/ugyldig_inntektsaar.json"))

            val response = skatteetatenClient.hentSkattekort("BR1234")

            response.status shouldBe ResponseStatus.UGYLDIG_INNTEKTSAAR.name
            response.arbeidsgiver shouldBe emptyList()
        }

        test("should handle skattekortopplysningerOK") {
            val skatteetatenClient = setupClient(readFile("/skatteetaten/hentSkattekort/skattekortopplysningerOK.json"))

            val response = skatteetatenClient.hentSkattekort("BR1234")

            response.status shouldBe ResponseStatus.FORESPOERSEL_OK.name
            response.arbeidsgiver shouldNotBeNull {
                size shouldBe 1
                this[0] shouldNotBeNull {
                    arbeidsgiveridentifikator.organisasjonsnummer shouldBe "312978083"
                    arbeidstaker.size shouldBe 1
                    arbeidstaker[0] shouldNotBeNull {
                        arbeidstakeridentifikator shouldBe "12345678901"
                        resultatForSkattekort shouldBe ResultatForSkattekort.SkattekortopplysningerOK.value
                        skattekort.shouldNotBeNull {
                            skattekortidentifikator shouldBe 54407
                            forskuddstrekk.size shouldBe 5
                            forskuddstrekk[0] shouldNotBeNull {
                                trekkode shouldBe Trekkode.LOENN_FRA_HOVEDARBEIDSGIVER.value
                                trekktabell.shouldNotBeNull {
                                    tabellnummer shouldBe "8140"
                                }
                            }
                            forskuddstrekk[1] shouldNotBeNull {
                                trekkode shouldBe Trekkode.LOENN_FRA_BIARBEIDSGIVER.value
                                trekkprosent.shouldNotBeNull {
                                    prosentsats.toDouble() shouldBe 43.0
                                }
                            }
                            tilleggsopplysning!!.size shouldBe 4
                            tilleggsopplysning shouldContainExactly
                                listOf(
                                    "oppholdPaaSvalbard",
                                    "kildeskattPaaPensjon",
                                    "oppholdITiltakssone",
                                    "kildeskattPaaLoenn",
                                )
                        }
                    }
                }
            }
        }
    })

fun setupClient(jsonFile: String): SkatteetatenClient {
    val mockEngine =
        MockEngine { _ ->
            respond(
                content = jsonFile,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
    val clientWithMockReply =
        HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
    val mockTokenClient =
        mockk<MaskinportenTokenClient> {
            coEvery { getAccessToken() } returns "token"
        }
    val skatteetatenClient = SkatteetatenClient(mockTokenClient, clientWithMockReply)
    return skatteetatenClient
}
