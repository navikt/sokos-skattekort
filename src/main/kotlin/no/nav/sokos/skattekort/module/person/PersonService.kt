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

    fun findOrCreatePersonByFnr(
        fnr: Personidentifikator,
        informasjon: String,
        brukerId: String? = null,
        tx: TransactionalSession,
    ): Person =
        PersonRepository.findPersonByFnr(tx, fnr)?.let { person ->
            AuditRepository.insert(tx, AuditTag.MOTTATT_FORESPOERSEL, person.id!!, informasjon, brukerId)
            person
        } ?: run {
            val personId =
                PersonRepository.insert(tx, fnr, LocalDate.now(), informasjon, brukerId)
                    ?: run {
                        logger.error(marker = TEAM_LOGS_MARKER) { "Kan ikke opprettet person med fnr: $fnr" }
                        throw PersonException("Kan ikke opprettet person med fnr: xxxx")
                    }
            logger.info(marker = TEAM_LOGS_MARKER) { "Opprett person fnr: $fnr" }
            PersonRepository.findPersonById(tx, PersonId(personId))
        }

    fun findPersonByFnr(
        tx: TransactionalSession,
        fnr: Personidentifikator,
    ): Person? = PersonRepository.findPersonByFnr(tx, fnr)

    fun flaggPerson(
        tx: TransactionalSession,
        personId: PersonId,
    ) = PersonRepository.flaggPerson(tx, personId)

    fun findPersonIdByPersonidentifikator(
        tx: TransactionalSession,
        personidentifikatorList: List<String>,
    ): PersonId? = FoedselsnummerRepository.findPersonIdByPersonidentifikator(tx, personidentifikatorList)

    fun updateFoedselsnummer(
        tx: TransactionalSession,
        newFoedselsnummer: Foedselsnummer,
    ) {
        FoedselsnummerRepository.insert(tx, newFoedselsnummer)
        AuditRepository.insert(tx, AuditTag.OPPDATERT_PERSONIDENTIFIKATOR, newFoedselsnummer.personId!!, "Oppdatert foedselsnummer: ${newFoedselsnummer.fnr.value}")
    }
}
