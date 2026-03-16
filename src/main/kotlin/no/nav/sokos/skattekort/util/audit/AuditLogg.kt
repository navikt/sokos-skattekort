package no.nav.sokos.skattekort.util.audit

/**
 * Loggeformat
 * https://sikkerhet.nav.no/docs/sikker-utvikling/auditlogging#beskrivelse-av-cef
 */

data class AuditLogg(
    val saksbehandler: String,
    val fnr: String,
    val brukerhandling: String,
) {
    private val version = "0"
    private val deviceVendor = "Utbetalingsportalen"
    private val deviceProduct = "sokos-skattekort"
    private val deviceVersion = "1.0"
    private val deviceEventClassId = "audit:access"
    private val name = "sokos-skattekort"
    private val severity = "INFO"

    fun logMessage(): String {
        val extension = "suid=$saksbehandler duid=$fnr end=${System.currentTimeMillis()} msg=$brukerhandling"

        return "CEF:$version|$deviceVendor|$deviceProduct|$deviceVersion|$deviceEventClassId|$name|$severity|$extension"
    }
}
