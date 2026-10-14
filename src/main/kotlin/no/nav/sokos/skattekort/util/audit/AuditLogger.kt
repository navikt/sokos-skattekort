package no.nav.sokos.skattekort.util.audit

import mu.KotlinLogging

private val auditlogger = KotlinLogging.logger("auditLogger")

object AuditLogger {
    fun auditLog(auditLoggData: AuditLogg) {
        auditlogger.info(auditLoggData.logMessage())
    }
}
