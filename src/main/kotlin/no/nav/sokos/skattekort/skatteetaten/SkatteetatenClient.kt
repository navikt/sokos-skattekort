package no.nav.sokos.skattekort.skatteetaten

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess

import no.nav.sokos.skattekort.config.PropertiesConfig
import no.nav.sokos.skattekort.security.MaskinportenTokenClient
import no.nav.sokos.skattekort.skatteetaten.svar.Root

class SkatteetatenClient(
    private val maskinportenTokenClient: MaskinportenTokenClient,
    private val client: HttpClient,
) {
    private val skatteetatenUrl = PropertiesConfig.getSkatteetatenProperties().skatteetatenApiUrl

    suspend fun bestillSkattekort(request: SkatteetatenBestillSkattekortRequest): SkatteetatenBestillSkattekortResponse {
        val url = "$skatteetatenUrl/api/forskudd/bestillSkattekort/"

        val response: HttpResponse =
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

    suspend fun hentSkattekort(bestillingsreferanse: String): Root {
        val url = "$skatteetatenUrl/api/forskudd/skattekortTilArbeidsgiver/svar/"

        val response =
            client.get(url) {
                bearerAuth(maskinportenTokenClient.getAccessToken())
                accept(ContentType.Application.Json)
                parameter("bestillingsreferanse", bestillingsreferanse)
            }

        if (!response.status.isSuccess()) {
            throw RuntimeException("Feil ved henting av skattekort: ${response.status.value} - ${response.bodyAsText()}")
        }

        return response.body<Root>()
    }
}
