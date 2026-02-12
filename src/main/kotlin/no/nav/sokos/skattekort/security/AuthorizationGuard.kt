package no.nav.sokos.skattekort.security

import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Permission validator for fine-grained access control.
 * Validates scopes (OBO tokens) and roles (M2M tokens) for endpoints.
 */
object AuthorizationGuard {
    /**
     * Get NAVident from OBO token, or null if M2M token.
     * Use this when you need to handle both OBO and M2M tokens differently.
     */
    fun ApplicationCall.getNavIdentOrNull(): String? {
        val principal = principal<JWTPrincipal>() ?: return null
        return principal.payload.getClaim(JWT_CLAIM_NAVIDENT)?.asString()
    }

    /**
     * Get calling system name from JWT token (azp_name or client_id).
     * Useful for logging which application is calling the endpoint.
     * Returns "Unknown" if not found.
     */
    fun ApplicationCall.getCallingSystem(): String {
        val principal = principal<JWTPrincipal>() ?: return "Unknown"
        val azpName =
            principal.payload.getClaim("azp_name")?.asString()
                ?: principal.payload.getClaim("client_id")?.asString()
        return TokenUtils.extractApplicationName(azpName)
    }

    /**
     * Require a specific scope (OBO token) OR a specific role (M2M token).
     * Returns true if authorized, false (and sends 403) if not.
     */
    fun ApplicationCall.requireScopeOrRole(scopeOrRole: String) {
        val principal =
            principal<JWTPrincipal>()
                ?: throw AuthenticationException("No principal found - authentication not configured")

        val callingSystem = getCallingSystem()

        // Check OBO token scope
        val scopes =
            principal.payload
                .getClaim("scp")
                ?.asString()
                ?.split(" ") ?: emptyList()
        if (AccessPolicy.hasRequiredScope(scopes, scopeOrRole)) {
            logger.debug { "Authorized: '$callingSystem' with OBO token has required scope '$scopeOrRole'" }
            return
        }

        // Check M2M token role
        val roles = principal.payload.getClaim("roles")?.asList(String::class.java) ?: emptyList()
        if (AccessPolicy.hasRequiredRole(roles, scopeOrRole)) {
            logger.debug { "Authorized: '$callingSystem' with M2M token has required role '$scopeOrRole'" }
            return
        }

        // Neither scope nor role found
        logger.warn {
            "Authorization failed: '$callingSystem' missing required scope/role '$scopeOrRole'. " +
                "Found scopes: $scopes, roles: $roles"
        }

        throw AuthorizationException("Missing required scope or role: $scopeOrRole")
    }

    /**
     * Require a specific scope (OBO token only).
     * Returns true if authorized, false (and sends 403) if not.
     */
    fun ApplicationCall.requireScope(requiredScope: String) {
        val principal =
            principal<JWTPrincipal>()
                ?: throw AuthenticationException("No principal found - authentication not configured")

        val callingSystem = getCallingSystem()
        val scopes =
            principal.payload
                .getClaim("scp")
                ?.asString()
                ?.split(" ") ?: emptyList()

        if (!AccessPolicy.hasRequiredScope(scopes, requiredScope)) {
            logger.warn {
                "Authorization failed: '$callingSystem' missing required scope '$requiredScope'. Found scopes: $scopes"
            }
            throw AuthorizationException("Missing required scope: $requiredScope")
        }

        logger.debug { "Authorized: '$callingSystem' has required scope '$requiredScope'" }
    }

    /**
     * Require a specific role (M2M token only).
     * Returns true if authorized, false (and sends 403) if not.
     */
    fun ApplicationCall.requireRole(requiredRole: String) {
        val principal =
            principal<JWTPrincipal>()
                ?: throw AuthenticationException("No principal found - authentication not configured")

        val callingSystem = getCallingSystem()
        val roles = principal.payload.getClaim("roles")?.asList(String::class.java) ?: emptyList()

        if (!AccessPolicy.hasRequiredRole(roles, requiredRole)) {
            logger.warn {
                "Authorization failed: '$callingSystem' missing required role '$requiredRole'. Found roles: $roles"
            }

            throw AuthorizationException("Missing required role: $requiredRole")
        }

        logger.debug { "Authorized: '$callingSystem' has required role '$requiredRole'" }
    }
}

class AuthorizationException(
    override val message: String,
) : Exception(message)

class AuthenticationException(
    override val message: String,
) : Exception(message)
