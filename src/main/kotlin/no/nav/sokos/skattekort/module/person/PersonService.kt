package no.nav.sokos.skattekort.module.person

import java.time.LocalDate
import javax.sql.DataSource

import kotlinx.coroutines.runBlocking
import kotlinx.datetime.toKotlinLocalDate

import kotliquery.TransactionalSession
import mu.KotlinLogging

import no.nav.sokos.skattekort.config.TEAM_LOGS_MARKER
import no.nav.sokos.skattekort.infrastructure.pdl.PdlClientService
import no.nav.sokos.skattekort.util.SQLUtils.transaction

private const val CHUNKED_SIZE = 1000

private val logger = KotlinLogging.logger { }

class PersonService(
    private val dataSource: DataSource,
    private val pdlClientService: PdlClientService,
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

    fun getPersonIdAndCheckFoedselsnumreIsUpdated(
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

        dataSource.transaction { tx ->
            runBlocking {
                val identInformasjonMap =
                    foedselsnummerList
                        .chunked(chunkedSize)
                        .flatMap { chunk -> pdlClientService.getIdenterBolk(chunk).entries }
                        .groupBy({ it.key }, { it.value })
                        .mapValues { entry -> entry.value.flatten() }

                foedselsnummerList.forEach { fnr ->
                    val identInformasjon = identInformasjonMap[fnr]
                    if (!identInformasjon.isNullOrEmpty()) {
                        val personId =
                            findPersonIdOrCreatePersonByFnr(
                                tx = tx,
                                fnr = Personidentifikator(fnr),
                                informasjon = "Opprett person",
                                brukerId = brukerId,
                            ).first
                        // oppdatert foedselsnumreWithPersonIdMap med mangler personId
                        foedselsnumreWithPersonIdMap[fnr] = personId

                        // sjekk om det fins nye identen som tilhønrer til personen
                        val ident = identInformasjon.first { !it.historisk }.ident
                        if (ident != fnr) {
                            logger.info(marker = TEAM_LOGS_MARKER) { "Oppdater personId=$personId med folkeregisteridentifikator=$ident" }
                            updateFoedselsnummer(
                                tx,
                                Foedselsnummer(
                                    personId = personId,
                                    gjelderFom = LocalDate.now().toKotlinLocalDate(),
                                    fnr = Personidentifikator(ident),
                                ),
                            )
                        }
                    } else {
                        logger.error(marker = TEAM_LOGS_MARKER) { "Ingen person funnet i PDL for fnr: $fnr" }
                        logger.error { "Ingen person funnet i PDL, sjekk Team Logs" }
                        foedselsnumreWithPersonIdMap.remove(fnr)
                    }
                }
            }
        }
        return foedselsnumreWithPersonIdMap
    }

    fun updateFoedselsnummer(
        tx: TransactionalSession,
        newFoedselsnummer: Foedselsnummer,
    ) {
        FoedselsnummerRepository.insert(tx, newFoedselsnummer)
        AuditRepository.insert(tx, AuditTag.OPPDATERT_PERSONIDENTIFIKATOR, newFoedselsnummer.personId!!, "Oppdatert foedselsnummer: ${newFoedselsnummer.fnr.value}")
    }
}
