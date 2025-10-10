package no.nav.sokos.skattekort.security

import java.time.Duration
import java.time.Instant
import java.util.Date
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.http.Parameters
import io.ktor.http.isSuccess
import mu.KotlinLogging

import no.nav.sokos.skattekort.config.PropertiesConfig

private val logger = KotlinLogging.logger {}

class MaskinportenTokenClient(
    private val client: HttpClient,
) {
    private val mutex = Mutex()
    private val timeLimit = Duration.ofSeconds(60)
    private val tokenCache = AtomicReference<AccessToken?>(null)
    private val maskinportenPropertoes: PropertiesConfig.MaskinportenPropertoes = PropertiesConfig.getMaskinportenProperties()

    suspend fun getAccessToken(): String =
        mutex.withLock {
            val nowPlusLimit = Instant.now().plus(timeLimit)
            val cachedToken = tokenCache.get()

            if (cachedToken == null || cachedToken.expiresAt < nowPlusLimit) {
                tokenCache.set(getMaskinportenToken())
            }

            tokenCache.get()!!.token
        }

    private suspend fun getMaskinportenToken(): AccessToken {
        val openIdConfiguration = client.get(maskinportenPropertoes.wellKnownUrl).body<OpenIdConfiguration>()
        val jwtAssertion = getJwtAssertion(openIdConfiguration.issuer, getSystembrukerClaim(maskinportenPropertoes.systemBrukerClaim))
        val response =
            client
                .submitForm(
                    url = openIdConfiguration.tokenEndpoint,
                    formParameters =
                        Parameters.build {
                            append("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer")
                            append("assertion", jwtAssertion)
                        },
                )

        return if (response.status.isSuccess()) {
            AccessToken(response.body<TokenResponse>())
        } else {
            logger.error("Kunne ikke hente accessToken, se sikker log for meldingen som string")
            val feilmelding = response.body<TokenError>()
            logger.error("Feil fra tokenprovider, Token: $jwtAssertion, Feilmelding: $feilmelding")
            throw Exception("Feil fra tokenprovider, Token: $jwtAssertion, Feilmelding: $feilmelding")
        }
    }

    private fun getJwtAssertion(
        audience: String,
        additionalClaims: Map<String, Any> = emptyMap(),
    ): String =
        SignedJWT(
            JWSHeader
                .Builder(JWSAlgorithm.RS256)
                .keyID(maskinportenProperties.rsaKey?.keyID)
                .type(JOSEObjectType.JWT)
                .build(),
            JWTClaimsSet
                .Builder()
                .issuer(maskinportenProperties.clientId)
                .audience(audience)
                .issueTime(Date.from(Instant.now()))
                .expirationTime(Date.from(Instant.now().plus(timeLimit)))
                .jwtID(UUID.randomUUID().toString())
                .claim("scope", maskinportenPropertoes.scopes)
                .apply { additionalClaims.forEach { (key, value) -> claim(key, value) } }
                .build(),
        ).apply {
            sign(RSASSASigner(maskinportenProperties.rsaKey?.toRSAPrivateKey()))
        }.serialize()

    private fun getSystembrukerClaim(orgNr: String) =
        mapOf(
            "authorization_details" to
                listOf(
                    mapOf(
                        "type" to "urn:altinn:systemuser",
                        "systemuser_org" to
                            mapOf(
                                "authority" to "iso6523-actorid-upis",
                                "ID" to "0192:$orgNr",
                            ),
                    ),
                ),
        )
}

private data class AccessToken(
    val token: String,
    val expiresAt: Instant,
) {
    constructor(tokenResponse: TokenResponse) :
        this(tokenResponse.accessToken, Instant.now().plusSeconds(tokenResponse.expiresIn))
}

@Serializable
private data class TokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("token_type") val tokenType: String,
    @SerialName("expires_in") val expiresIn: Long,
    val scope: String,
)

@Serializable
private data class TokenError(
    @SerialName("error") val error: String,
    @SerialName("error_description") val errorDescription: String,
    @SerialName("error_uri") val errorUri: String? = null,
)

@Serializable
private data class OpenIdConfiguration(
    @SerialName("jwks_uri") val jwksUri: String,
    @SerialName("issuer") val issuer: String,
    @SerialName("token_endpoint") val tokenEndpoint: String,
)
