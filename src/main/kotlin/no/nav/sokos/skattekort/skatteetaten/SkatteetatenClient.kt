package no.nav.sokos.skattekort.skatteetaten

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotliquery.TransactionalSession

import no.nav.sokos.skattekort.config.PropertiesConfig
import no.nav.sokos.skattekort.infrastructure.UnleashIntegration
import no.nav.sokos.skattekort.module.skattekort.BestillingBatchRepository
import no.nav.sokos.skattekort.security.MaskinportenTokenClient
import no.nav.sokos.skattekort.skatteetaten.bestillskattekort.BestillSkattekortRequest
import no.nav.sokos.skattekort.skatteetaten.bestillskattekort.BestillSkattekortResponse
import no.nav.sokos.skattekort.skatteetaten.hentskattekort.HentSkattekortResponse

class SkatteetatenClient(
    private val maskinportenTokenClient: MaskinportenTokenClient,
    private val client: HttpClient,
    private val featureToggles: UnleashIntegration,
) {
    private val skatteetatenUrl = PropertiesConfig.getSkatteetatenProperties().skatteetatenApiUrl

    suspend fun bestillSkattekort(request: BestillSkattekortRequest): BestillSkattekortResponse {
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

        return response.body<BestillSkattekortResponse>()
    }

    suspend fun hentSkattekort(
        tx: TransactionalSession?,
        bestillingsreferanse: String,
    ): HentSkattekortResponse? {
        val url = "$skatteetatenUrl/api/forskudd/skattekortTilArbeidsgiver/svar/$bestillingsreferanse"

        val response =
            client.get(url) {
                bearerAuth(maskinportenTokenClient.getAccessToken())
                accept(ContentType.Application.Json)
            }

        if (response.status == HttpStatusCode.NoContent) {
            if (featureToggles.isLagreMottatteBestillingerEnabled()) {
                if (tx == null) error("Kan ikke lagre mottatte data i tekstformat uten tilgang til en transaksjon")
                BestillingBatchRepository.insertMottatteData(tx, bestillingsreferanse, "")
            }
            return null
        }

        if (!response.status.isSuccess()) {
            throw RuntimeException("Feil ved henting av skattekort: ${response.status.value} - ${response.bodyAsText()}")
        }
        if (featureToggles.isLagreMottatteBestillingerEnabled()) {
            if (tx == null) error("Kan ikke lagre mottatte data i tekstformat uten tilgang til en transaksjon")
            BestillingBatchRepository.insertMottatteData(tx, bestillingsreferanse, response.bodyAsText())
        }

        return response.body<HentSkattekortResponse>()
    }
}
