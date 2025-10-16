package no.nav.sokos.skattekort.skatteetaten

import kotlinx.serialization.json.Json

class SkatteetatenClient {
    fun bestillSkattekort(request: SkatteetatenBestillSkattekortRequest): SkatteetatenBestillSkattekortResponse {
        val url = "https://api-test.sits.no/api/forskudd/bestillSkattekort/"
        val client =
            java.net.http.HttpClient
                .newHttpClient()

        val requestBody = Json.encodeToString(request)

        val request =
            java.net.http.HttpRequest
                .newBuilder()
                .uri(java.net.URI.create(url))
                .header("Content-Type", "application/json")
                .POST(
                    java.net.http.HttpRequest.BodyPublishers
                        .ofString(requestBody),
                ).build()
        val response =
            client.send(
                request,
                java.net.http.HttpResponse.BodyHandlers
                    .ofString(),
            )

        return Json.decodeFromString<SkatteetatenBestillSkattekortResponse>(response.body())
    }
}
