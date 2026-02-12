package no.nav.sokos.skattekort.security

import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal

const val JWT_CLAIM_NAVIDENT = "NAVident"

/**
 * Utility for retrieving NAVident claim from authenticated JWT tokens.
 * NAVident identifies the saksbehandler (case handler) who initiated the request.
 */
object NavIdentClaim {
    /**
     * Extracts the NAVident (saksbehandler ID) from the JWT token.
     *
     * @return NAVident of the authenticated saksbehandler
     * @throws IllegalStateException if authentication is not configured or NAVident claim is missing
     */
    fun ApplicationCall.getSaksbehandler(): String {
        val principal =
            principal<JWTPrincipal>()
                ?: throw IllegalStateException("No JWTPrincipal found in call - authentication not configured correctly")

        return principal.payload.getClaim(JWT_CLAIM_NAVIDENT)?.asString()
            ?: throw IllegalStateException("NAVident claim is missing - should have been validated by authentication provider")
    }
}
