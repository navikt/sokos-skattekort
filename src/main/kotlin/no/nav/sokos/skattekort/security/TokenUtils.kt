package no.nav.sokos.skattekort.security

/**
 * Utility functions for JWT token handling.
 */
object TokenUtils {
    /**
     * Extract application name from azp_name or client_id claim.
     * Strips namespace/cluster prefix (e.g., "dev-gcp:okonomi:sokos-utbetalingsportalen" -> "sokos-utbetalingsportalen").
     *
     * @param azpNameOrClientId The azp_name or client_id claim value
     * @return The application name, or "Unknown" if null/empty
     */
    fun extractApplicationName(azpNameOrClientId: String?): String {
        if (azpNameOrClientId.isNullOrBlank()) return "Unknown"
        return azpNameOrClientId.split(":").last()
    }
}
