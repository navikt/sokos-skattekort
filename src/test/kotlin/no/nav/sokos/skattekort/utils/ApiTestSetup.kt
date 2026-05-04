package no.nav.sokos.skattekort.utils

import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.auth.authenticate
import io.ktor.server.routing.Route
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication

import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.security.mock.oauth2.withMockOAuth2Server
import no.nav.sokos.skattekort.config.PropertiesConfig
import no.nav.sokos.skattekort.config.commonConfig
import no.nav.sokos.skattekort.config.jsonConfig
import no.nav.sokos.skattekort.config.securityConfig
import no.nav.sokos.skattekort.security.JWT_CLAIM_NAVIDENT
import no.nav.sokos.skattekort.security.JWT_CLAIM_ROLES
import no.nav.sokos.skattekort.security.JWT_CLAIM_SCOPES
import no.nav.sokos.skattekort.security.Role
import no.nav.sokos.skattekort.security.Scope

const val AUTH_PROVIDER_NAME = "azureAd"

fun MockOAuth2Server.mockAuthConfig(): PropertiesConfig.AzureAdProperties =
    PropertiesConfig.AzureAdProperties(
        clientId = "default",
        wellKnownUrl = wellKnownUrl("default").toString(),
        providerName = AUTH_PROVIDER_NAME,
    )

fun MockOAuth2Server.oboToken(
    navIdent: String = "aUser",
    scopes: List<Scope> = Scope.entries,
): String =
    issueToken(
        issuerId = "default",
        claims =
            mapOf(
                JWT_CLAIM_NAVIDENT to navIdent,
                JWT_CLAIM_SCOPES to scopes.joinToString(" ") { it.value },
            ),
    ).serialize()

fun MockOAuth2Server.m2mToken(roles: List<Role> = Role.entries): String =
    issueToken(
        issuerId = "default",
        claims =
            mapOf(
                JWT_CLAIM_ROLES to roles.map { it.value }.toTypedArray(),
            ),
    ).serialize()

fun withApiTestApplication(
    routeSetup: Route.() -> Unit,
    block: suspend ApplicationTestBuilder.(authServer: MockOAuth2Server, jsonClient: HttpClient) -> Unit,
) = withMockOAuth2Server {
    val authServer = this
    testApplication {
        application {
            commonConfig()
            securityConfig(authServer.mockAuthConfig())
            routing {
                authenticate(AUTH_PROVIDER_NAME) {
                    routeSetup()
                }
            }
        }
        val jsonClient =
            createClient {
                install(ContentNegotiation) {
                    json(jsonConfig)
                }
            }
        block(authServer, jsonClient)
    }
}
