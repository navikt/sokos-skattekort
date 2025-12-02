package no.nav.sokos.skattekort.audit

import mu.KotlinLogging

private val auditlogger = KotlinLogging.logger("auditLogger")

class AuditLogger {
    fun auditLog(auditLoggData: AuditLogg) {
        auditlogger.info(auditLoggData.logMessage())
    }
}
