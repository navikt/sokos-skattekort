package no.nav.sokos.skattekort.infrastructure.tilgangsmaskin

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.request.accept
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import mu.KotlinLogging

import no.nav.sokos.skattekort.config.HttpClientConfig.httpClient as defaultHttpClient
import no.nav.sokos.skattekort.config.PropertiesConfig
import no.nav.sokos.skattekort.config.TEAM_LOGS_MARKER
import no.nav.sokos.skattekort.security.AzuredTokenClient
import no.nav.tilgangsmaskinen.ProblemDetailResponse

private val logger = KotlinLogging.logger {}

class TilgangsmaskinClientService(
    private val httpClient: HttpClient = defaultHttpClient,
    private val tilgangsmaskinUrl: String = PropertiesConfig.tilgangsmaskinProperties.tilgangsmaskinUrl,
    private val azuredTokenClient: AzuredTokenClient = AzuredTokenClient(defaultHttpClient, PropertiesConfig.tilgangsmaskinProperties.tilgangsmaskinScope),
) {
    suspend fun checkSaksbehandlerAccess(
        saksbehandlerIdent: String,
        fnr: String,
    ): ProblemDetailResponse? {
        val accessToken = azuredTokenClient.getSystemToken()
        val response =
            httpClient.post("$tilgangsmaskinUrl/api/v1/ccf/kjerne/$saksbehandlerIdent") {
                contentType(ContentType.Application.Json)
                accept(ContentType.Application.Json)
                bearerAuth(accessToken)
                setBody(fnr)
            }
        return when (response.status) {
            HttpStatusCode.NoContent -> null
            HttpStatusCode.Forbidden -> {
                val problemDetailResponse = response.body<ProblemDetailResponse>()
                logger.warn(marker = TEAM_LOGS_MARKER) { "Tilgangskontroll feilet for saksbehandler $saksbehandlerIdent, response: $problemDetailResponse" }
                problemDetailResponse
            }

            else -> throw ClientRequestException(response, "Uventet svar fra tilgangsmaskinen")
        }
    }
}
