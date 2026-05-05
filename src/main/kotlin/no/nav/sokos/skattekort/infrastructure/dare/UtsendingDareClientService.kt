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
import mu.KotlinLogging

import no.nav.sokos.skattekort.config.httpClient as defaultHttpClient
import no.nav.sokos.skattekort.api.model.v2.SkattekortDTO
import no.nav.sokos.skattekort.config.PropertiesConfig
import no.nav.sokos.skattekort.config.TEAM_LOGS_MARKER
import no.nav.sokos.skattekort.security.AzuredTokenClient

private val logger = KotlinLogging.logger {}

class UtsendingDareClientService(
    private val httpClient: HttpClient = defaultHttpClient,
    private val darePocUrl: String = PropertiesConfig.darePocProperties.darePocUrl,
    private val azuredTokenClient: AzuredTokenClient = AzuredTokenClient(defaultHttpClient, PropertiesConfig.darePocProperties.darePocScope),
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
