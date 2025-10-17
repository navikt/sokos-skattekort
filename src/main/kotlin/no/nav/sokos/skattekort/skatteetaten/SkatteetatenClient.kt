package no.nav.sokos.skattekort.skatteetaten

import kotlinx.serialization.json.Json

import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType

import no.nav.sokos.skattekort.config.httpClient
import no.nav.sokos.skattekort.security.MaskinportenTokenClient

class SkatteetatenClient(
    private val maskinportenTokenClient: MaskinportenTokenClient,
) {
    suspend fun bestillSkattekort(request: SkatteetatenBestillSkattekortRequest): SkatteetatenBestillSkattekortResponse {
        // Flyttes til nais-config når vi skal ha forskjellige miljøer
        val url = "https://api-test.sits.no/api/forskudd/bestillSkattekort/"

        val requestBody = Json.encodeToString(request)

        val response =
            httpClient.post(url) {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer ${maskinportenTokenClient.getAccessToken()}")
                setBody(requestBody)
            }

        return response.body<SkatteetatenBestillSkattekortResponse>()
    }
}
