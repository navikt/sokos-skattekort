package no.nav.sokos.skattekort.skattekort

import javax.sql.DataSource

import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

import mu.KotlinLogging

import no.nav.sokos.skattekort.config.PropertiesConfig
import no.nav.sokos.skattekort.config.TEAM_LOGS_MARKER
import no.nav.sokos.skattekort.dto.SkattekortDTO
import no.nav.sokos.skattekort.forespoersel.Foedselsnummerkategori
import no.nav.sokos.skattekort.forespoersel.Foedselsnummerkategori.*
import no.nav.sokos.skattekort.person.PersonRepository
import no.nav.sokos.skattekort.person.PersonService
import no.nav.sokos.skattekort.person.Personidentifikator
import no.nav.sokos.skattekort.security.Saksbehandler
import no.nav.sokos.skattekort.util.SQLUtils.transaction
import no.nav.sokos.skattekort.util.audit.AuditLogg
import no.nav.sokos.skattekort.util.audit.AuditLogger

private val logger = KotlinLogging.logger {}

class SkattekortService(
    val dataSource: DataSource,
    val personService: PersonService,
    private val auditLogger: AuditLogger,
) {
    fun getSingleSkattekortForEachYear(
        fnr: String,
        inntektsaar: Int? = null,
        saksbehandler: Saksbehandler? = null,
    ) = getSkattekort(fnr, inntektsaar, saksbehandler).distinctBy { it.inntektsaar }

    fun getSkattekort(
        fnr: String,
        inntektsaar: Int? = null,
        saksbehandler: Saksbehandler? = null,
    ): List<SkattekortDTO> {
        logger.info(marker = TEAM_LOGS_MARKER) { "Henter skattekort for person: $fnr, for år: $inntektsaar" }

        // Sjekker om fnr er reelt og krever i så fall det er kallt med obo-token
        if (GYLDIGE.erGyldig(fnr)) {
            requireNotNull(inntektsaar) { "Må oppgi inntektsår ved oppslag på reelle fnr" }
            requireNotNull(saksbehandler) { "Oppslag på reelle skattekort må gjøres på vegne av en saksbehandler" }
            auditLogger.auditLog(AuditLogg(saksbehandler = saksbehandler.ident, fnr = fnr))
        }

        return dataSource
            .transaction { tx ->
                val person = PersonRepository.findPersonByFnr(tx, Personidentifikator(fnr)) ?: return@transaction emptyList()
                SkattekortRepository
                    .findAllByPersonId(
                        tx,
                        person.id!!,
                        inntektsaar,
                        adminRole = false,
                    ).map(::SkattekortDTO)
            }.toList()
    }

    fun createSkattekort(
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

        if (GYLDIGE.erGyldig(fnr)) {
            requireNotNull(saksbehandler) { "Manuell opprettelse av reelle skattekort må gjøres på vegne av en saksbehandler" }
            auditLogger.auditLog(AuditLogg(saksbehandler = saksbehandler.ident, fnr = fnr))
        }

        return dataSource.transaction { tx ->
            val (personId) =
                personService.findPersonIdOrCreatePersonByFnr(
                    fnr = Personidentifikator(fnr),
                    informasjon = "Skattekort manuelt opprettet person",
                    tx = tx,
                )
            val today =
                Clock.System
                    .now()
                    .toLocalDateTime(TimeZone.currentSystemDefault())
                    .date
            // Evt opprette Bestilling for Testnorge-brukere?
            val skattekort =
                skattekortDTO.toDomainSkattekort(
                    personId = personId,
                    utstedtDato = skattekortDTO.utstedtDato ?: today,
                    identifikator = null,
                    kilde = SkattekortKilde.MANUELL,
                )
            val id = SkattekortId(SkattekortRepository.insert(tx, skattekort))

            Syntetisering.evtSyntetiserSkattekort(skattekort, id)?.let { (syntetisertSkattekort, _) ->
                SkattekortRepository.insert(tx, syntetisertSkattekort, "manuelt syntetisk")
            }
        }
    }
}
