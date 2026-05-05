package no.nav.sokos.skattekort.infrastructure.pdl

import com.expediagroup.graphql.client.types.GraphQLClientError
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import mu.KotlinLogging

import no.nav.sokos.skattekort.config.HttpClientConfig.httpClient as defaultHttpClient
import no.nav.pdl.HentIdenterBolk
import no.nav.pdl.HentPersonBolk
import no.nav.pdl.hentidenterbolk.IdentInformasjon
import no.nav.pdl.hentpersonbolk.Person
import no.nav.sokos.skattekort.config.PropertiesConfig
import no.nav.sokos.skattekort.config.TEAM_LOGS_MARKER
import no.nav.sokos.skattekort.security.AzuredTokenClient

private val logger = KotlinLogging.logger {}

private const val BEHANDLINGSKATALOGNUMMER = "B749"

class PdlClientService(
    private val httpClient: HttpClient = defaultHttpClient,
    private val pdlUrl: String = PropertiesConfig.pdlProperties.pdlUrl,
    private val azuredTokenClient: AzuredTokenClient = AzuredTokenClient(defaultHttpClient, PropertiesConfig.pdlProperties.pdlScope),
) {
    suspend fun getIdenterBolk(identer: List<String>): Map<String, List<IdentInformasjon>> {
        val request = HentIdenterBolk(HentIdenterBolk.Variables(identer))

        val accessToken = azuredTokenClient.getSystemToken()

        logger.debug { "Henter identer fra PDL" }
        val response =
            httpClient.post("$pdlUrl/graphql") {
                bearerAuth(accessToken)
                header("behandlingsnummer", BEHANDLINGSKATALOGNUMMER)
                contentType(ContentType.Application.Json)
                setBody(request)
            }

        return when {
            response.status.isSuccess() -> {
                val result = response.body<GraphQLResponse<HentIdenterBolk.Result>>()
                if (result.errors?.isNotEmpty() == true) {
                    handleErrors(result.errors)
                }
                result.data
                    ?.hentIdenterBolk
                    ?.map { item -> item.ident to (item.identer ?: emptyList()) }
                    ?.groupBy({ it.first }, { it.second })
                    ?.mapValues { entry -> entry.value.flatten() }
                    .orEmpty()
            }

            else -> {
                throw ClientRequestException(
                    response,
                    "Noe gikk galt ved oppslag mot PDL",
                )
            }
        }
    }

    suspend fun getPersonNavnBulk(identer: List<String>): Map<String, Person> {
        val request = HentPersonBolk(HentPersonBolk.Variables(identer))

        val accessToken = azuredTokenClient.getSystemToken()

        logger.debug { "Henter navn fra PDL" }
        val response =
            httpClient.post("$pdlUrl/graphql") {
                bearerAuth(accessToken)
                header("behandlingsnummer", BEHANDLINGSKATALOGNUMMER)
                contentType(ContentType.Application.Json)
                setBody(request)
            }

        return when {
            response.status.isSuccess() -> {
                val result = response.body<GraphQLResponse<HentPersonBolk.Result>>()
                if (result.errors?.isNotEmpty() == true) {
                    handleErrors(result.errors)
                }
                result.data
                    ?.hentPersonBolk
                    ?.filter { item -> item.person != null }
                    ?.associate { item -> item.ident to item.person!! } ?: emptyMap()
            }

            else -> {
                logger.error(marker = TEAM_LOGS_MARKER) { "Noe gikk galt ved oppslag mot PDL for ident: $identer" }
                throw ClientRequestException(
                    response,
                    "Noe gikk galt ved oppslag mot PDL",
                )
            }
        }
    }

    private fun handleErrors(errors: List<GraphQLClientError>) {
        val errorMessage = errors.joinToString { it.message }
        val exceptionMessage = "Message: $errorMessage"
        throw PdlException(
            exceptionMessage,
        )
    }
}

data class PdlException(
    override val message: String,
) : Exception(message)
