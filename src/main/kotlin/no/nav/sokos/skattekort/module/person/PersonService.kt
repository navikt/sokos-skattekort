package no.nav.sokos.skattekort.module.person

import java.time.LocalDate
import javax.sql.DataSource

import kotliquery.TransactionalSession
import mu.KotlinLogging

import no.nav.sokos.skattekort.config.TEAM_LOGS_MARKER
import no.nav.sokos.skattekort.util.SQLUtils.withTx

private val logger = KotlinLogging.logger { }

class PersonService(
    private val dataSource: DataSource,
) {
    fun getPersonList(
        count: Int = 30,
        startId: String? = null,
        tx: TransactionalSession? = null,
    ): List<Person> =
        dataSource.withTx(tx) { session ->
            PersonRepository.getAllPersonById(session, count, startId)
        }

    fun findPersonIdOrCreatePersonByFnr(
        fnr: Personidentifikator,
        informasjon: String,
        brukerId: String? = null,
        tx: TransactionalSession,
    ): Pair<PersonId, Boolean> =
        PersonRepository.findPersonIdByFnr(tx, fnr)?.let { personId ->
            AuditRepository.insert(tx, AuditTag.MOTTATT_FORESPOERSEL, personId, informasjon, brukerId)
            personId to false
        } ?: run {
            val personId =
                PersonRepository.insert(tx, fnr, LocalDate.now(), informasjon, brukerId)
                    ?: run {
                        logger.error(marker = TEAM_LOGS_MARKER) { "Kan ikke opprettet person med fnr: $fnr" }
                        throw PersonException("Kan ikke opprettet person med fnr: xxxx")
                    }
            logger.info(marker = TEAM_LOGS_MARKER) { "Opprett person fnr: $fnr" }
            PersonId(personId) to true
        }

    fun updateFoedselsnummer(
        tx: TransactionalSession,
        newFoedselsnummer: Foedselsnummer,
    ) {
        FoedselsnummerRepository.insert(tx, newFoedselsnummer)
        AuditRepository.insert(tx, AuditTag.OPPDATERT_PERSONIDENTIFIKATOR, newFoedselsnummer.personId!!, "Oppdatert foedselsnummer: ${newFoedselsnummer.fnr.value}")
    }
}
