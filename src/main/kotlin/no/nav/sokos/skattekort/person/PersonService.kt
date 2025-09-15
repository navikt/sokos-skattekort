package no.nav.sokos.skattekort.person

import javax.sql.DataSource

import kotliquery.TransactionalSession
import kotliquery.sessionOf
import kotliquery.using

class PersonService(
    val dataSource: DataSource,
    val personRepository: PersonRepository,
) {
    fun list(
        count: Int = 10,
        startId: String? = null,
    ): List<Person> =
        using(sessionOf(dataSource)) { session ->
            session.transaction { tx ->
                personRepository.list(tx, count, startId)
            }
        }

    fun findOrCreateByOffNr(
        personRepository: PersonRepository,
        tx: TransactionalSession,
        offNr: String,
        grunn: String, // Årsak for å lage aktør, hvis nødvendig
    ): Pair<PersonId, Boolean> =
        (
            personRepository.internalFindPersonByFnr(tx, offNr)?.id?.let {
                // Fnr er allerede i databasen, vi returnerer eksisterende person
                aId ->
                Pair(aId, false)
            }
        ) ?: personRepository.let {
            // Fnr ikke funnet, ny person opprettes
            _ ->
            Pair(createPersonIdAndFnr(this.personRepository, tx, offNr, grunn), true)
        }

    fun createPersonIdAndFnr(
        personRepository: PersonRepository,
        tx: TransactionalSession,
        fnr: String,
        grunn: String,
    ): PersonId {
        val personId = personRepository.createNewPersonId(tx)
        personRepository.audit(tx, AuditTag.OPPRETTET_PERSON, personId, grunn)
        personRepository.createNewFnr(tx, personId, fnr)
        return personId
    }
}
