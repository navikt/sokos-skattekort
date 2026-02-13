package no.nav.sokos.skattekort.security

import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import mu.KotlinLogging

const val JWT_CLAIM_NAVIDENT = "NAVident"

private val logger = KotlinLogging.logger {}

/**
 * Permission validator for fine-grained access control.
 * Validates scopes (OBO tokens) and roles (M2M tokens) for endpoints.
 */
object AuthorizationGuard {
    private const val CLAIM_SCOPES = "scp"
    private const val CLAIM_ROLES = "roles"

    /**
     * Get NAVident from OBO token, or null if M2M token.
     * Use this when you need to handle both OBO and M2M tokens differently.
     */
    fun ApplicationCall.getNavIdentOrNull(): String? = principal<JWTPrincipal>()?.payload?.getClaim(JWT_CLAIM_NAVIDENT)?.asString()

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
        val principal = requirePrincipal()
        val callingSystem = getCallingSystem()

        val scopes = principal.scopes()
        if (AccessPolicy.hasRequiredScope(scopes, scopeOrRole)) {
            logger.debug { "Authorized: `$callingSystem` with OBO token has required scope `$scopeOrRole`" }
            return
        }

        val roles = principal.roles()
        if (AccessPolicy.hasRequiredRole(roles, scopeOrRole)) {
            logger.debug { "Authorized: `$callingSystem` with M2M token has required role `$scopeOrRole`" }
            return
        }

        logger.warn {
            "Authorization failed: `$callingSystem` missing required scope/role `$scopeOrRole`. " +
                "Found scopes: $scopes, roles: $roles"
        }
        throw AuthorizationException("Missing required scope or role: $scopeOrRole")
    }

    /**
     * Require a specific scope (OBO token only).
     * Returns true if authorized, false (and sends 403) if not.
     */

    fun ApplicationCall.requireScope(requiredScope: String) =
        require(
            claimName = "scope",
            required = requiredScope,
            values = requirePrincipal().scopes(),
            has = { scopes -> AccessPolicy.hasRequiredScope(scopes, requiredScope) },
        )

    /**
     * Require a specific role (M2M token only).
     * Returns true if authorized, false (and sends 403) if not.
     */
    fun ApplicationCall.requireRole(requiredRole: String) =
        require(
            claimName = "role",
            required = requiredRole,
            values = requirePrincipal().roles(),
            has = { roles -> AccessPolicy.hasRequiredRole(roles, requiredRole) },
        )

    private fun ApplicationCall.require(
        claimName: String,
        required: String,
        values: List<String>,
        has: (List<String>) -> Boolean,
    ) {
        val callingSystem = getCallingSystem()
        if (!has(values)) {
            logger.warn { "Authorization failed: `$callingSystem` missing required $claimName $required. Found $claimName${if (claimName.endsWith("s")) "" else "s"}: $values" }
            throw AuthorizationException("Missing required $claimName: $required")
        }
        logger.debug { "Authorized: `$callingSystem` has required $claimName `$required`" }
    }

    private fun ApplicationCall.requirePrincipal(): JWTPrincipal = principal<JWTPrincipal>() ?: throw AuthenticationException("No principal found - authentication not configured")

    private fun JWTPrincipal.scopes(): List<String> =
        payload
            .getClaim(CLAIM_SCOPES)
            ?.asString()
            ?.split(" ") ?: emptyList()

    private fun JWTPrincipal.roles(): List<String> = payload.getClaim(CLAIM_ROLES)?.asList(String::class.java) ?: emptyList()
}

class AuthorizationException(
    override val message: String,
) : Exception(message)

class AuthenticationException(
    override val message: String,
) : Exception(message)
