package no.nav.sokos.skattekort.security

import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import mu.KotlinLogging

const val JWT_CLAIM_NAVIDENT = "NAVident"
const val JWT_CLAIM_ROLES = "roles"
const val JWT_CLAIM_SCOPES = "scp"

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
    fun ApplicationCall.requirePermission(
        requiredScope: Scope,
        requiredRole: Role,
    ) {
        val principal =
            principal<JWTPrincipal>() ?: throw AuthenticationException("No principal found - authentication not configured")
        val callingSystem = getCallingSystem()

        // Check OBO token scope
        val scopes =
            principal.payload
                .getClaim("scp")
                ?.asString()
                ?.split(" ") ?: emptyList()
        if (scopes.isNotEmpty() && AccessPolicy.hasRequiredScope(scopes, requiredScope.value)) {
            logger.debug { "Authorized: '$callingSystem' with OBO token has required scope '$scopes'" }
            return
        }

        // Check M2M token role
        val roles = principal.payload.getClaim("roles")?.asList(String::class.java) ?: emptyList()
        if (roles.isNotEmpty() && AccessPolicy.hasRequiredRole(roles, requiredRole.value)) {
            logger.debug { "Authorized: '$callingSystem' with M2M token has required role '$roles'" }
            return
        }

        // Neither scope nor role found
        logger.warn {
            "Authorization failed: '$callingSystem' missing required scope/role. Found scopes: $scopes, roles: $roles"
        }

        throw AuthorizationException("Missing required scope or role")
    }

    /**
     * Require a specific scope (OBO token only).
     * Returns true if authorized, false (and sends 403) if not.
     */

    fun ApplicationCall.requireScope(requiredScope: Scope) =
        require(
            claimName = "scope",
            required = requiredScope.value,
            values = requirePrincipal().scopes(),
            has = { scopes -> AccessPolicy.hasRequiredScope(scopes, requiredScope.value) },
        )

    /**
     * Require a specific role (M2M token only).
     * Returns true if authorized, false (and sends 403) if not.
     */
    fun ApplicationCall.requireRole(requiredRole: Role) {
        val test = requirePrincipal().roles()
        return require(
            claimName = "role",
            required = requiredRole.value,
            values = requirePrincipal().roles(),
            has = { roles -> AccessPolicy.hasRequiredRole(roles, requiredRole.value) },
        )
    }

    private fun ApplicationCall.require(
        claimName: String,
        required: String,
        values: List<String>,
        has: (List<String>) -> Boolean,
    ) {
        val callingSystem = getCallingSystem()
        if (!has(values)) {
            logger.warn { "Authorization failed: `$callingSystem` missing required $claimName $required. Found $claimName${if (claimName.endsWith("s")) "" else "s"}: $values" }
            throw AuthorizationException("Missing required $claimName")
        }
        logger.debug { "Authorized: `$callingSystem` has required $claimName" }
    }

    private fun ApplicationCall.requirePrincipal(): JWTPrincipal = principal<JWTPrincipal>() ?: throw AuthenticationException("No principal found - authentication not configured")

    private fun JWTPrincipal.scopes(): List<String> =
        payload
            .getClaim(JWT_CLAIM_SCOPES)
            ?.asString()
            ?.split(" ") ?: emptyList()

    private fun JWTPrincipal.roles(): List<String> = payload.getClaim(JWT_CLAIM_ROLES)?.asList(String::class.java) ?: emptyList()
}

class AuthorizationException(
    override val message: String,
) : Exception(message)

class AuthenticationException(
    override val message: String,
) : Exception(message)
