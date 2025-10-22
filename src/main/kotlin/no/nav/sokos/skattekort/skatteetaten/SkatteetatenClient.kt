package no.nav.sokos.skattekort.skatteetaten

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess

import no.nav.sokos.skattekort.config.httpClient
import no.nav.sokos.skattekort.security.MaskinportenTokenClient

class SkatteetatenClient(
    private val maskinportenTokenClient: MaskinportenTokenClient = MaskinportenTokenClient(httpClient),
    private val client: HttpClient = httpClient,
) {
    suspend fun bestillSkattekort(request: SkatteetatenBestillSkattekortRequest): SkatteetatenBestillSkattekortResponse {
        // Flyttes til nais-config når vi skal ha forskjellige miljøer
        val url = "https://api-test.sits.no/api/forskudd/bestillSkattekort/"

        val response =
            client.post(url) {
                contentType(ContentType.Application.Json)
                bearerAuth(maskinportenTokenClient.getAccessToken())
                setBody(request)
            }

        if (!response.status.isSuccess()) {
            throw RuntimeException("Feil ved bestilling av skattekort: ${response.status.value} - ${response.bodyAsText()}")
        }

        return response.body<SkatteetatenBestillSkattekortResponse>()
    }
}
