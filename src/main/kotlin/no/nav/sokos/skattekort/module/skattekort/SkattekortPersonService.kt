package no.nav.sokos.skattekort.module.skattekort

import javax.sql.DataSource

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

import mu.KotlinLogging

import no.nav.sokos.skattekort.audit.AuditLogg
import no.nav.sokos.skattekort.audit.AuditLogger
import no.nav.sokos.skattekort.config.PropertiesConfig
import no.nav.sokos.skattekort.config.TEAM_LOGS_MARKER
import no.nav.sokos.skattekort.dto.SkattekortDTO
import no.nav.sokos.skattekort.module.forespoersel.Foedselsnummerkategori
import no.nav.sokos.skattekort.module.person.PersonRepository
import no.nav.sokos.skattekort.module.person.PersonService
import no.nav.sokos.skattekort.module.person.Personidentifikator
import no.nav.sokos.skattekort.security.Saksbehandler
import no.nav.sokos.skattekort.util.SQLUtils.transaction

private val logger = KotlinLogging.logger {}

class SkattekortPersonService(
    val dataSource: DataSource,
    val personService: PersonService,
    private val auditLogger: AuditLogger,
) {
    fun hentSkattekortPerson(
        fnr: String,
        inntektsaar: Short? = null,
        saksbehandler: Saksbehandler? = null,
    ): List<SkattekortDTO> {
        logger.info(marker = TEAM_LOGS_MARKER) { "Henter skattekort for person: $fnr, for år: $inntektsaar" }

        if (PropertiesConfig.getApplicationProperties().environment == PropertiesConfig.Environment.PROD) {
            requireNotNull(saksbehandler) { "Oppslag i produksjonsmiljø må gjøres på vegne av en saksbehandler" }
            auditLogger.auditLog(AuditLogg(saksbehandler = saksbehandler.ident, fnr = fnr))
        }
        require(!(Foedselsnummerkategori.GYLDIGE.erGyldig(fnr) && saksbehandler == null)) { "Oppslag på skattekort for reelle fnr må gjøres på vegne av en saksbehandler" }
        val foedselsnummerkategori = Foedselsnummerkategori.valueOf(PropertiesConfig.getApplicationProperties().gyldigeFnr)

        if (!foedselsnummerkategori.erGyldig(fnr)) {
            logger.warn(marker = TEAM_LOGS_MARKER) { "Ugyldig fnr for miljø ${PropertiesConfig.getApplicationProperties().environment}($fnr)" }
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
                    ).map(::SkattekortDTO)
            }.toList()
    }

    fun opprettSkattekort(
        fnr: String,
        skattekortDTO: SkattekortDTO,
        saksbehandler: Saksbehandler?,
    ): Long? {
        logger.info(marker = TEAM_LOGS_MARKER) { "Oppretter skattekort for person: $fnr, for år: ${skattekortDTO.inntektsaar}" }

        val foedselsnummerkategori = Foedselsnummerkategori.valueOf(PropertiesConfig.getApplicationProperties().gyldigeFnr)
        if (!foedselsnummerkategori.erGyldig(fnr)) {
            logger.warn(marker = TEAM_LOGS_MARKER) { "Ugyldig fnr for miljø ${PropertiesConfig.getApplicationProperties().environment}($fnr)" }
            return null
        }

        if (PropertiesConfig.getApplicationProperties().environment == PropertiesConfig.Environment.PROD) {
            requireNotNull(saksbehandler) { "Manuell opprettelse av reelle skattekort må gjøres på vegne av en saksbehandler" }
            auditLogger.auditLog(AuditLogg(saksbehandler = saksbehandler.ident, fnr = fnr))
        }

        require(!(Foedselsnummerkategori.GYLDIGE.erGyldig(fnr) && saksbehandler == null)) { "Manuell opprettelse av skattekort for reelle fnr må gjøres på vegne av en saksbehandler" }

        return dataSource.transaction { tx ->
            val (personId) =
                personService.findPersonIdOrCreatePersonByFnr(
                    fnr = Personidentifikator(fnr),
                    informasjon = "Skattekort manuelt opprettet for tidligere ukjent person",
                    tx = tx,
                )
            val today =
                kotlin.time.Clock.System
                    .now()
                    .toLocalDateTime(TimeZone.currentSystemDefault())
                    .date
            // Evt opprette Bestilling for Testnorge-brukere?
            val skattekort =
                skattekortDTO.toDomainSkattekort(
                    personId = personId,
                    utstedtDato = skattekortDTO.utstedtDato ?: today,
                    identifikator = "dolly",
                    kilde = SkattekortKilde.MANUELL,
                    resultatForSkattekort = ResultatForSkattekort.SkattekortopplysningerOK,
                )
            SkattekortRepository.insert(tx, skattekort)
        }
    }
}
