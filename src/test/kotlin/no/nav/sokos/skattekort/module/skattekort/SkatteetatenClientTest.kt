package no.nav.sokos.skattekort.module.skattekort

import java.nio.file.Files
import java.nio.file.Paths

import kotlinx.serialization.json.Json

import io.kotest.core.spec.style.FunSpec
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

import no.nav.sokos.skattekort.security.MaskinportenTokenClient
import no.nav.sokos.skattekort.skatteetaten.SkatteetatenClient

class SkatteetatenClientTest :
    FunSpec({
        test("should handle specific json response") {
            val jsonResponse = Files.readString(Paths.get("src/test/resources/skatteetaten/ugyldig_inntektsaar.json"))
            val mockEngine =
                MockEngine { request ->
                    respond(
                        content = jsonResponse,
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

            val response =
                skatteetatenClient.hentSkattekort("BR1234")

            response.status shouldBe ResponseStatus.UGYLDIG_INNTEKTSAAR.name
        }
    })
