package no.nav.sokos.skattekort.security

/**
 * Allowed scopes for OBO (On-Behalf-Of) tokens.
 * These represent user-initiated operations.
 */
enum class Scope(
    val value: String,
) {
    BESTILLE_SCOPE("bestille-scope"),
    STATUS_SCOPE("status-scope"),
    HENT_SKATTEKORT_SCOPE("hent-skattekort-scope"),
    HENT_NAVN_SCOPE("hent-navn-scope"),
    OPPRETT_SCOPE("opprett-scope"),
    ;

    override fun toString() = value
}

/**
 * Allowed roles for M2M (Machine-to-Machine) tokens.
 */
enum class Role(
    val value: String,
) {
    BESTILLE_ROLE("bestille-role"),
    OPPRETT_ROLE("opprett-role"),
    HENT_SKATTEKORT_ROLE("hent-skattekort-role"),
    ;

    override fun toString() = value
}

/**
 * Defines allowed scopes and roles for fine-grained access control.
 *
 * OBO (On-Behalf-Of) tokens use scopes from the 'scp' claim.
 * M2M (Machine-to-Machine) tokens use roles from the 'roles' claim.
 */
object AccessPolicy {
    /**
     * Allowed scopes for OBO tokens.
     */
    val ALLOWED_SCOPES = Scope.entries.map { it.value }.toSet()

    /**
     * Allowed roles for M2M tokens.
     */
    val ALLOWED_ROLES = Role.entries.map { it.value }.toSet()

    /**
     * Check if the provided scopes contain a specific required scope.
     */
    fun hasRequiredScope(
        scopes: List<String>,
        requiredScope: String,
    ): Boolean = requiredScope in scopes && requiredScope in ALLOWED_SCOPES

    /**
     * Check if the provided roles contain a specific required role.
     */
    fun hasRequiredRole(
        roles: List<String>,
        requiredRole: String,
    ): Boolean = requiredRole in roles && requiredRole in ALLOWED_ROLES
}
