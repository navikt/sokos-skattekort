package no.nav.sokos.skattekort.security

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.server.auth.jwt.JWTPrincipal

object TokenTestUtils {
    fun generateToken(
        navIdent: String? = null,
        scopes: List<String> = emptyList(),
        roles: List<String> = emptyList(),
    ): String {
        val builder = JWT.create().withSubject("1234567890")

        if (navIdent != null) {
            builder.withClaim(JWT_CLAIM_NAVIDENT, navIdent)
        }

        if (scopes.isNotEmpty()) {
            builder.withClaim("scp", scopes.joinToString(" "))
        }

        if (roles.isNotEmpty()) {
            builder.withArrayClaim("roles", roles.toTypedArray())
        }

        return builder.sign(Algorithm.HMAC256("secret"))
    }

    fun String.getPrincipal(): JWTPrincipal {
        val decoded = JWT.require(Algorithm.HMAC256("secret")).build().verify(this)
        return JWTPrincipal(decoded)
    }
}
