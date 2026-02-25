package no.nav.sokos.skattekort.infrastructure.skatteetaten

import java.time.Duration

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.custom
import io.github.resilience4j.kotlin.circuitbreaker.decorateSuspendFunction
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.expectSuccess
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
import no.nav.sokos.skattekort.infrastructure.METRICS_NAMESPACE
import no.nav.sokos.skattekort.infrastructure.Metrics
import no.nav.sokos.skattekort.infrastructure.Metrics.counter
import no.nav.sokos.skattekort.infrastructure.UnleashIntegration
import no.nav.sokos.skattekort.infrastructure.skatteetaten.bestillskattekort.BestillSkattekortRequest
import no.nav.sokos.skattekort.infrastructure.skatteetaten.bestillskattekort.BestillSkattekortResponse
import no.nav.sokos.skattekort.infrastructure.skatteetaten.hentskattekort.HentSkattekortResponse
import no.nav.sokos.skattekort.security.MaskinportenTokenClient
import no.nav.sokos.skattekort.skattekortbestilling.BestillingBatchRepository

class SkatteetatenClient(
    private val maskinportenTokenClient: MaskinportenTokenClient,
    private val client: HttpClient
) {
    private val skatteetatenUrl = PropertiesConfig.getSkatteetatenProperties().skatteetatenApiUrl

    suspend fun bestillSkattekort(request: BestillSkattekortRequest): BestillSkattekortResponse =
        circuitBreaker
            .decorateSuspendFunction {
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

                response.body<BestillSkattekortResponse>()
            }.invoke()

    suspend fun hentSkattekort(
        bestillingsreferanse: String,
    ): HentSkattekortResponse? {
        return circuitBreaker
            .decorateSuspendFunction {
                val url = "$skatteetatenUrl/api/forskudd/skattekortTilArbeidsgiver/svar/$bestillingsreferanse"

                val response =
                    client.get(url) {
                        bearerAuth(maskinportenTokenClient.getAccessToken())
                        accept(ContentType.Application.Json)
                        expectSuccess = false
                    }

                if (response.status == HttpStatusCode.NoContent || response.status == HttpStatusCode.ServiceUnavailable) {
                    return@decorateSuspendFunction null
                }

                if (!response.status.isSuccess()) {
                    throw RuntimeException("Feil ved henting av skattekort: ${response.status.value} - ${response.bodyAsText()}")
                }
                
                response.body<HentSkattekortResponse>()
            }.invoke()
    }

    companion object {
        val circuitBreaker =
            Metrics.circuitBreakerRegistry.circuitBreaker(
                "${METRICS_NAMESPACE}_skatteetatenClientCircuitBreaker",
                custom()
                    .failureRateThreshold(10f)
                    .slidingWindowSize(10)
                    .waitDurationInOpenState(Duration.ofMinutes(5))
                    .permittedNumberOfCallsInHalfOpenState(1)
                    .build(),
            )
    }
}
