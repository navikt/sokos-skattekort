package no.nav.sokos.skattekort.infrastructure.skatteetaten

import kotlinx.serialization.json.Json

import io.kotest.core.spec.style.BehaviorSpec
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

import no.nav.sokos.skattekort.config.PropertiesConfig
import no.nav.sokos.skattekort.infrastructure.skatteetaten.bestillskattekort.bestillSkattekortRequest
import no.nav.sokos.skattekort.person.Personidentifikator
import no.nav.sokos.skattekort.security.MaskinportenTokenClient
import no.nav.sokos.skattekort.skattekort.ResponseStatus
import no.nav.sokos.skattekort.skattekort.ResultatForSkattekort
import no.nav.sokos.skattekort.skattekort.Trekkode
import no.nav.sokos.skattekort.utils.TestUtils.readFile

class SkatteetatenClientTest :
    BehaviorSpec({
        Given("en skatteetaten-klient som bestiller skattekort") {
            val bestillSkattekortRequest =
                bestillSkattekortRequest(
                    2025,
                    listOf(
                        Personidentifikator("01010100001"),
                    ),
                    "",
                )
            val skatteetatenClient = setupClient(readFile("/skatteetaten/bestillSkattekort/bestillSkattekortResponse.json"))

            When("bestilling sendes") {
                Then("returneres dialog- og bestillingsreferanse") {
                    val response = skatteetatenClient.bestillSkattekort(bestillSkattekortRequest)

                    response shouldNotBeNull {
                        dialogreferanse shouldBe "1"
                        bestillingsreferanse shouldBe "TEST8128"
                    }
                }
            }
        }

        Given("en skatteetaten-klient som mottar ugyldig inntektsår") {
            val skatteetatenClient = setupClient(readFile("/skatteetaten/hentSkattekort/ugyldig_inntektsaar.json"))

            When("skattekortet hentes") {
                Then("returneres status for ugyldig inntektsår uten arbeidsgivere") {
                    val response = skatteetatenClient.hentSkattekort("BR1234")

                    response shouldNotBeNull {
                        status shouldBe ResponseStatus.UGYLDIG_INNTEKTSAAR.name
                        arbeidsgiver shouldBe emptyList()
                    }
                }
            }
        }

        Given("en skatteetaten-klient som mottar gyldige skattekortopplysninger") {
            val skatteetatenClient = setupClient(readFile("/skatteetaten/hentSkattekort/skattekortopplysningerOK.json"))

            When("skattekortet hentes") {
                Then("returneres skattekortopplysninger med forventet struktur") {
                    val response = skatteetatenClient.hentSkattekort("BR1234")

                    response shouldNotBeNull {
                        status shouldBe ResponseStatus.FORESPOERSEL_OK.name
                        arbeidsgiver shouldNotBeNull {
                            size shouldBe 1
                            this[0] shouldNotBeNull {
                                arbeidsgiveridentifikator.organisasjonsnummer shouldBe "312978083"
                                arbeidstaker.size shouldBe 1
                                arbeidstaker[0] shouldNotBeNull {
                                    arbeidstakeridentifikator shouldBe "01010112345"
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
    val skatteetatenClient =
        SkatteetatenClient(
            clientWithMockReply,
            PropertiesConfig.skatteetatenProperties.skatteetatenUrl,
            mockTokenClient,
        )
    return skatteetatenClient
}
