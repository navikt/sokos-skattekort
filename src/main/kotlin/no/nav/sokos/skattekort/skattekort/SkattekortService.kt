package no.nav.sokos.skattekort.skattekort

import java.time.Year
import javax.sql.DataSource

import io.github.resilience4j.core.functions.Either
import mu.KotlinLogging

import no.nav.sokos.skattekort.config.PropertiesConfig
import no.nav.sokos.skattekort.config.TEAM_LOGS_MARKER
import no.nav.sokos.skattekort.dto.SkattekortDTO
import no.nav.sokos.skattekort.forespoersel.Foedselsnummerkategori
import no.nav.sokos.skattekort.forespoersel.Foedselsnummerkategori.GYLDIGE
import no.nav.sokos.skattekort.infrastructure.tilgangsmaskin.TilgangsmaskinClientService
import no.nav.sokos.skattekort.person.PersonRepository
import no.nav.sokos.skattekort.person.PersonService
import no.nav.sokos.skattekort.person.Personidentifikator
import no.nav.sokos.skattekort.security.Saksbehandler
import no.nav.sokos.skattekort.util.SQLUtils.transaction
import no.nav.sokos.skattekort.util.audit.AuditLogg
import no.nav.sokos.skattekort.util.audit.AuditLogger
import no.nav.tilgangsmaskinen.ProblemDetailResponse

private val logger = KotlinLogging.logger {}

class SkattekortService(
    private val dataSource: DataSource,
    private val personService: PersonService,
    private val tilgangsmaskinClientService: TilgangsmaskinClientService,
    private val auditLogger: AuditLogger,
) {
    suspend fun getSingleSkattekortForEachYear(
        fnr: String,
        inntektsaar: Int? = null,
        saksbehandler: Saksbehandler? = null,
    ): Either<ProblemDetailResponse, List<Skattekort>> = getSkattekort(fnr, inntektsaar, saksbehandler).map { it.distinctBy { skattekort -> skattekort.inntektsaar } }

    suspend fun getSkattekort(
        fnr: String,
        inntektsaar: Int? = null,
        saksbehandler: Saksbehandler? = null,
    ): Either<ProblemDetailResponse, List<Skattekort>> {
        logger.info(marker = TEAM_LOGS_MARKER) { "Henter skattekort for person: $fnr, for år: $inntektsaar" }
        saksbehandler?.let {
            tilgangsmaskinClientService.checkSaksbehandlerAccess(it.ident, fnr)?.let { response ->
                return Either.left(response)
            }
        }

        // Sjekker om fnr er reelt og krever i så fall det er kallt med obo-token
        if (GYLDIGE.erGyldig(fnr)) {
            requireNotNull(saksbehandler) { "Oppslag på reelle skattekort må gjøres på vegne av en saksbehandler" }
            auditLogger.auditLog(AuditLogg(saksbehandler = saksbehandler.ident, fnr = fnr, brukerhandling = "NAV-ansatt har søkt etter skattekort for bruker"))
        }

        return Either.right(
            dataSource
                .transaction { tx ->
                    val person = PersonRepository.findPersonByFnr(tx, Personidentifikator(fnr)) ?: return@transaction emptyList()
                    SkattekortRepository
                        .findAllByPersonId(
                            tx,
                            person.id!!,
                            inntektsaar,
                            adminRole = false,
                        )
                }.toList(),
        )
    }

    fun createManualSkattekort(
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
            auditLogger.auditLog(AuditLogg(saksbehandler = saksbehandler.ident, fnr = fnr, brukerhandling = "NAV-ansatt har opprettet skattekort for bruker"))
        } else {
            auditLogger.auditLog(AuditLogg(saksbehandler = "DOLLY", fnr = fnr, brukerhandling = "Dolly har opprettet skattekort for bruker"))
        }

        return dataSource.transaction { tx ->
            val (personId) =
                personService.findPersonIdOrCreatePersonByFnr(
                    fnr = Personidentifikator(fnr),
                    informasjon = "Skattekort manuelt opprettet person",
                    tx = tx,
                )
            // Evt opprette Bestilling for Testnorge-brukere?
            val skattekort =
                skattekortDTO.toDomainSkattekort(
                    personId = personId,
                    utstedtDato =
                        when (skattekortDTO.resultatForSkattekort) {
                            ResultatForSkattekort.SkattekortopplysningerOK.value -> skattekortDTO.utstedtDato
                            ResultatForSkattekort.IkkeSkattekort.value, ResultatForSkattekort.IkkeTrekkplikt.value -> null
                            else -> null // Kan også vurdere å kaste feilmelding
                        },
                    identifikator = null,
                    kilde = SkattekortKilde.MANUELL,
                )
            val id = SkattekortId(SkattekortRepository.insert(tx, skattekort))

            Syntetisering.evtSyntetiserSkattekort(skattekort, id)?.let { (syntetisertSkattekort, _) ->
                SkattekortRepository.insert(tx, syntetisertSkattekort)
            }
        }
    }

    fun deleteSkattekortForYear(inntektsaar: Int = Year.now().minusYears(2).value) {
        runCatching {
            logger.info { "Deleting skattekort for year: $inntektsaar start" }
            val skattekortIdList = dataSource.transaction { tx -> SkattekortRepository.getAllIdByInntektsaar(tx, inntektsaar) }
            skattekortIdList.chunked(10000).forEach { chunk ->
                dataSource.transaction { tx -> SkattekortRepository.deleteBatch(tx, chunk) }
            }
            logger.info {
                "Deleting ${skattekortIdList.size} skattekort for year: $inntektsaar finished"
            }
        }.onFailure { exception ->
            logger.error("Failed to delete skattekort for year: $inntektsaar", exception)
        }
    }

    fun genertManueltGenerertSkattekort() {
        logger.info { "Genererer manuelt genererte skattekort" }

        runCatching {
            dataSource.transaction { tx ->
                val skattekortList = SkattekortRepository.getManueltGenerertSkattekort(tx)
                logger.info { "Fant ${skattekortList.size} manuelt genererte skattekort som skal syntetiseres" }
                skattekortList.forEach { skattekort ->
                    Syntetisering.evtSyntetiserSkattekort(skattekort, skattekort.id!!)?.let { (syntetisertSkattekort, _) ->
                        val manueltSkattekort = syntetisertSkattekort.copy(id = SkattekortId(skattekort.id.value.inc()))
                        // logger.info { "Setter inn manuelt generert skattekort med id ${manueltSkattekort.id} for skattekort $manueltSkattekort" }
                        SkattekortRepository.insert(tx, manueltSkattekort)
                    }
                }
            }
            logger.info { "Manuelt genererte skattekort syntetisert" }
        }.onFailure { exception ->
            logger.error(exception) { "Failed to generate manuelt generated skattekort" }
        }
    }
}
