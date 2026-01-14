package no.nav.sokos.skattekort.module.skattekort

import javax.sql.DataSource

import mu.KotlinLogging

import no.nav.sokos.skattekort.audit.AuditLogg
import no.nav.sokos.skattekort.audit.AuditLogger
import no.nav.sokos.skattekort.config.TEAM_LOGS_MARKER
import no.nav.sokos.skattekort.config.UnauthorizedException
import no.nav.sokos.skattekort.module.forespoersel.Foedselsnummerkategori.DOLLY
import no.nav.sokos.skattekort.module.forespoersel.Foedselsnummerkategori.TENOR
import no.nav.sokos.skattekort.module.person.PersonRepository
import no.nav.sokos.skattekort.module.person.Personidentifikator
import no.nav.sokos.skattekort.security.Saksbehandler
import no.nav.sokos.skattekort.util.SQLUtils.transaction
import no.nav.sokos.skattekort.util.Util

private val logger = KotlinLogging.logger {}

class SkattekortPersonService(
    val dataSource: DataSource,
    private val auditLogger: AuditLogger,
) {
    fun hentSkattekortPerson(
        fnr: String,
        inntektsaar: Short? = null,
        saksbehandler: Saksbehandler? = null,
    ): List<Skattekort> {
        logger.info(marker = TEAM_LOGS_MARKER) { "Henter skattekort for person: $fnr, for år: $inntektsaar" }
        if (saksbehandler != null) {
            auditLogger.auditLog(AuditLogg(saksbehandler = saksbehandler.ident, fnr = fnr))
        } else if (!DOLLY.erGyldig(fnr) && !TENOR.erGyldig(fnr)) {
            throw UnauthorizedException("Oppslag på skattekort for reelle fnr må gjøres på vegne av en saksbehandler")
        }
        return dataSource.transaction { tx ->
            val person = PersonRepository.findPersonByFnr(tx, Personidentifikator(fnr)) ?: return@transaction emptyList()

            val allYears =
                if (inntektsaar != null) {
                    require(!Util.lovligeInntektsaarAaHenteSkattekortFor().contains(inntektsaar)) {
                        "Ugyldig inntektsår"
                    }
                    listOf(inntektsaar)
                } else {
                    Util.lovligeInntektsaarAaHenteSkattekortFor()
                }
            allYears
                .flatMap { year ->
                    SkattekortRepository.findAllByPersonId(
                        tx,
                        person.id!!,
                        year.toInt(),
                        adminRole = false,
                    )
                }.toList()
        }
    }
}
