package no.nav.sokos.skattekort.security

import com.auth0.jwt.JWT
import io.ktor.http.HttpHeaders
import io.ktor.server.application.ApplicationCall

const val JWT_CLAIM_NAVIDENT = "NAVident"
const val JWT_CLAIM_GROUPS = "groups"

object AuthToken {
    fun getSaksbehandler(call: ApplicationCall): NavIdent {
        val oboToken =
            call.request.headers[HttpHeaders.Authorization]?.removePrefix("Bearer ")
                ?: throw kotlin.Error("Could not get token from request header")

        val navIdent = getNAVIdentFromToken(oboToken)
        return NavIdent(navIdent)
    }

    private fun getNAVIdentFromToken(token: String): String {
        val decodedJWT = JWT.decode(token)
        return decodedJWT.claims[JWT_CLAIM_NAVIDENT]?.asString()
            ?: throw kotlin.RuntimeException("Missing NAVident in private claims")
    }
}

data class NavIdent(
    val ident: String,
    val roller: List<String> = emptyList(),
)
