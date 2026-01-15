package no.nav.sokos.skattekort.module.skattekort

import javax.sql.DataSource

import mu.KotlinLogging

import no.nav.sokos.skattekort.api.skattekortpersonapi.v1.Arbeidstaker
import no.nav.sokos.skattekort.audit.AuditLogg
import no.nav.sokos.skattekort.audit.AuditLogger
import no.nav.sokos.skattekort.config.PropertiesConfig
import no.nav.sokos.skattekort.config.TEAM_LOGS_MARKER
import no.nav.sokos.skattekort.config.UnauthorizedException
import no.nav.sokos.skattekort.module.forespoersel.Foedselsnummerkategori
import no.nav.sokos.skattekort.module.person.PersonRepository
import no.nav.sokos.skattekort.module.person.Personidentifikator
import no.nav.sokos.skattekort.security.Saksbehandler
import no.nav.sokos.skattekort.util.SQLUtils.transaction

private val logger = KotlinLogging.logger {}

class SkattekortPersonService(
    val dataSource: DataSource,
    private val auditLogger: AuditLogger,
) {
    fun hentSkattekortPerson(
        fnr: String,
        inntektsaar: Short? = null,
        saksbehandler: Saksbehandler? = null,
    ): List<Arbeidstaker> {
        logger.info(marker = TEAM_LOGS_MARKER) { "Henter skattekort for person: $fnr, for år: $inntektsaar" }

        if (PropertiesConfig.getApplicationProperties().environment != PropertiesConfig.Environment.PROD) {
            if (saksbehandler == null) {
                logger.warn(marker = TEAM_LOGS_MARKER) { "Mangler saksbehandler i produksjonsmiljø" }
                throw UnauthorizedException("Mangler saksbehandler!")
            }
            auditLogger.auditLog(AuditLogg(saksbehandler = saksbehandler.ident, fnr = fnr))
        } else {
            val foedselsnummerkategori = Foedselsnummerkategori.valueOf(PropertiesConfig.getApplicationProperties().gyldigeFnr)

            if (!foedselsnummerkategori.erGyldig(fnr)) {
                logger.warn(marker = TEAM_LOGS_MARKER) { "Ugyldig fnr-kategori for miljø ${PropertiesConfig.getApplicationProperties().environment}($fnr)" }
            }
            throw UnauthorizedException("Oppslag på skattekort for reelle fnr må gjøres på vegne av en saksbehandler")
        }
        return dataSource
            .transaction { tx ->
                val person = PersonRepository.findPersonByFnr(tx, Personidentifikator(fnr)) ?: return@transaction emptyList()

                SkattekortRepository
                    .findAllByPersonId(
                        tx,
                        person.id!!,
                        inntektsaar?.toInt(),
                        adminRole = false,
                    ).map { skattekortItem ->
                        Arbeidstaker(
                            skattekortItem.inntektsaar.toLong(),
                            fnr,
                            skattekortItem,
                        )
                    }
            }.toList()
    }
}
