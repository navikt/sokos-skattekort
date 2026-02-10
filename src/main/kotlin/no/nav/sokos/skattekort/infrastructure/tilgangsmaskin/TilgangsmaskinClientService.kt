package no.nav.sokos.skattekort.infrastructure.tilgangsmaskin

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.request.accept
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.plugins.di.annotations.Named
import mu.KotlinLogging
import org.apache.hc.core5.http.message.MessageSupport.header

import no.nav.sokos.skattekort.config.TEAM_LOGS_MARKER
import no.nav.sokos.skattekort.security.AzuredTokenClient
import no.nav.tilgangsmaskinen.ProblemDetailResponse

private val logger = KotlinLogging.logger {}

class TilgangsmaskinClientService(
    private val httpClient: HttpClient,
    @Named("tilgangsmaskinUrl") private val tilgangsmaskinUrl: String,
    @Named("tilgangsmaksinAzuredTokenClient") private val azuredTokenClient: AzuredTokenClient,
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
                header(HttpHeaders.Authorization, "Bearer $accessToken")
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
