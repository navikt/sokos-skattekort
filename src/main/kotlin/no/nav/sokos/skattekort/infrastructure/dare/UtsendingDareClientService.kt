package no.nav.sokos.skattekort.infrastructure.dare

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.request.accept
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.server.plugins.di.annotations.Named
import mu.KotlinLogging

import no.nav.sokos.skattekort.DAREPOC_AZURED_TOKEN_CLIENT
import no.nav.sokos.skattekort.DAREPOC_URL
import no.nav.sokos.skattekort.config.TEAM_LOGS_MARKER
import no.nav.sokos.skattekort.dto.v2.SkattekortDTO
import no.nav.sokos.skattekort.security.AzuredTokenClient

private val logger = KotlinLogging.logger {}

class UtsendingDareClientService(
    private val httpClient: HttpClient,
    @Named(DAREPOC_URL) private val darePocUrl: String,
    @Named(DAREPOC_AZURED_TOKEN_CLIENT) private val azuredTokenClient: AzuredTokenClient,
) {
    suspend fun sendSkattekort(skattekortDTO: SkattekortDTO) {
        val accessToken = azuredTokenClient.getSystemToken()
        val response =
            httpClient.post("$darePocUrl/api/skattekort/motta-skattekort") {
                contentType(ContentType.Application.Json)
                accept(ContentType.Application.Json)
                bearerAuth(accessToken)
                setBody(skattekortDTO)
            }
        return when {
            response.status.isSuccess() -> {
                logger.info { "Skattekort utsending vellykket til DARE POC" }
                logger.info(marker = TEAM_LOGS_MARKER) { "Skattekort for ${skattekortDTO.fnr}" }
            }
            else -> throw ClientRequestException(response, "Uventet svar fra DARE POC ved utsending av skattekort: ${response.status.value}, ${response.body<String>()}")
        }
    }
}
