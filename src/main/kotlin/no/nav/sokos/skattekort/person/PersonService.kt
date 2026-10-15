package no.nav.sokos.skattekort.person

import java.time.LocalDate
import javax.sql.DataSource

import io.github.resilience4j.core.functions.Either
import kotliquery.TransactionalSession
import mu.KotlinLogging

import no.nav.pdl.hentidenterbolk.IdentInformasjon
import no.nav.sokos.skattekort.config.DatabaseConfig
import no.nav.sokos.skattekort.config.PropertiesConfig
import no.nav.sokos.skattekort.config.TEAM_LOGS_MARKER
import no.nav.sokos.skattekort.forespoersel.Foedselsnummerkategori
import no.nav.sokos.skattekort.infrastructure.pdl.PdlClientService
import no.nav.sokos.skattekort.infrastructure.tilgangsmaskin.TilgangsmaskinClientService
import no.nav.sokos.skattekort.security.Saksbehandler
import no.nav.sokos.skattekort.util.SQLUtils.transaction
import no.nav.tilgangsmaskinen.ProblemDetailResponse

private const val CHUNKED_SIZE = 1000

private val logger = KotlinLogging.logger { }

class PersonService(
    private val dataSource: DataSource = DatabaseConfig.dataSource,
    private val pdlClientService: PdlClientService,
    private val tilgangsmaskinClientService: TilgangsmaskinClientService,
) {
    fun findPersonIdOrCreatePersonByFnr(
        fnr: Personidentifikator,
        informasjon: String,
        brukerId: String? = null,
        tx: TransactionalSession,
    ): Pair<PersonId, Boolean> =
        PersonRepository.findPersonIdByFnr(tx, fnr)?.let { personId -> personId to false } ?: run {
            val personId =
                PersonRepository.insert(tx, fnr, LocalDate.now(), informasjon, brukerId)
                    ?: run {
                        logger.error(marker = TEAM_LOGS_MARKER) { "Kan ikke opprettet person med fnr: $fnr" }
                        throw PersonException("Kan ikke opprettet person med fnr: xxxx")
                    }
            logger.info(marker = TEAM_LOGS_MARKER) { "Opprett person fnr: $fnr" }
            PersonId(personId) to true
        }

    suspend fun getPersonIdAndCheckFoedselsnumreIsUpdated(
        fnrList: List<String>,
        brukerId: String? = null,
        chunkedSize: Int = CHUNKED_SIZE,
    ): Map<String, PersonId?> {
        val foedselsnumreWithPersonIdMap =
            dataSource
                .transaction { tx ->
                    fnrList
                        .chunked(chunkedSize)
                        .flatMap { chunk -> FoedselsnummerRepository.findPersonIdByFnrList(tx, chunk).entries }
                        .associate { it.toPair() }
                }.toMutableMap()

        val foedselsnummerList = foedselsnumreWithPersonIdMap.filterValues { it == null }.keys.toList()
        // alle fnr har funnet med personId, skip PDL sjekk
        if (foedselsnummerList.isEmpty()) {
            return foedselsnumreWithPersonIdMap
        }

        val identInformasjonMap =
            foedselsnummerList
                .chunked(chunkedSize)
                .flatMap { chunk -> pdlClientService.getIdenterBolk(chunk).entries }
                .groupBy({ it.key }, { it.value })
                .mapValues { entry -> entry.value.flatten() }

        dataSource.transaction { tx ->
            foedselsnummerList.forEach { fnr ->
                handleIdentifier(
                    tx = tx,
                    fnr = fnr,
                    identInformasjon = identInformasjonMap[fnr],
                    brukerId = brukerId,
                    personIdByFnr = foedselsnumreWithPersonIdMap,
                )
            }
        }
        return foedselsnumreWithPersonIdMap
    }

    private fun handleIdentifier(
        tx: TransactionalSession,
        fnr: String,
        identInformasjon: List<IdentInformasjon>?,
        brukerId: String?,
        personIdByFnr: MutableMap<String, PersonId?>,
    ) {
        if (identInformasjon.isNullOrEmpty()) {
            logger.error(marker = TEAM_LOGS_MARKER) { "Ingen person funnet i PDL for fnr: $fnr" }
            logger.error { "Ingen person funnet i PDL, sjekk TEAM LOGS" }
            personIdByFnr.remove(fnr)
            return
        }

        val ident = identInformasjon.first { !it.historisk }.ident
        val isHistoricalFnr = identInformasjon.any { it.historisk && it.ident == fnr }

        if (isHistoricalFnr) {
            PersonRepository.findPersonIdByFnr(tx, Personidentifikator(ident))?.let { personId ->
                logger.info(marker = TEAM_LOGS_MARKER) { "Folkeregisteridentifikator=$ident allerede registrert, registrerer gamle FNR: $fnr i DB" }
                FoedselsnummerRepository.insertByExistingFnr(tx, fnr, ident)
                AuditRepository.insert(tx, AuditTag.OPPDATERT_PERSONIDENTIFIKATOR, personId, "Oppdatert gamle foedselsnummer: $fnr")
                personIdByFnr[fnr] = personId
                return
            }
        }

        val personId =
            findPersonIdOrCreatePersonByFnr(
                tx = tx,
                fnr = Personidentifikator(fnr),
                informasjon = "Opprett person",
                brukerId = brukerId,
            ).first
        personIdByFnr[fnr] = personId

        if (ident != fnr) {
            logger.info(marker = TEAM_LOGS_MARKER) { "Oppdater personId=$personId med folkeregisteridentifikator=$ident" }
            updateFoedselsnummer(
                tx,
                Foedselsnummer(
                    personId = personId,
                    gjelderFom = LocalDate.now(),
                    fnr = Personidentifikator(ident),
                ),
            )
        }
    }

    fun updateFoedselsnummer(
        tx: TransactionalSession,
        newFoedselsnummer: Foedselsnummer,
    ) {
        FoedselsnummerRepository.insert(tx, newFoedselsnummer)
        AuditRepository.insert(
            tx,
            AuditTag.OPPDATERT_PERSONIDENTIFIKATOR,
            requireNotNull(newFoedselsnummer.personId) {
                "Foedselsnummer mangler personId"
            },
            "Oppdatert foedselsnummer: ${newFoedselsnummer.fnr.value}",
        )
    }

    suspend fun getAuditLogs(
        fnr: String,
        saksbehandler: Saksbehandler,
    ): Either<ProblemDetailResponse, List<Audit>> {
        tilgangsmaskinClientService.checkSaksbehandlerAccess(saksbehandler.ident, fnr)?.let { response ->
            return Either.left(response)
        }
        return dataSource.transaction { tx ->
            val personId =
                PersonRepository.findPersonIdByFnr(tx, Personidentifikator(fnr))
                    ?: return@transaction Either.right(emptyList())
            Either.right(AuditRepository.getAuditByPersonId(tx, personId))
        }
    }

    fun validateFoedselsnummer(fnrList: List<String>): List<String> {
        val foedselsnummerkategori = Foedselsnummerkategori.valueOf(PropertiesConfig.applicationProperties.gyldigeFnr)
        return fnrList.filter { fnr ->
            foedselsnummerkategori.erGyldig(fnr).also { valid ->
                if (!valid) {
                    logger.error(marker = TEAM_LOGS_MARKER) { "fjernet fnr som ikke er gyldig fra kallet: $fnr" }
                }
            }
        }
    }
}
