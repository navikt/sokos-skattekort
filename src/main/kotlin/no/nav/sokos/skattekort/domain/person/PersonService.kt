package no.nav.sokos.skattekort.domain.person

import java.time.LocalDate

import com.zaxxer.hikari.HikariDataSource
import kotliquery.sessionOf
import kotliquery.using
import mu.KotlinLogging

import no.nav.sokos.skattekort.util.SQLUtils.transaction

private val logger = KotlinLogging.logger { }

class PersonService(
    private val dataSource: HikariDataSource,
) {
    fun list(
        count: Int = 10,
        startId: String? = null,
    ): List<Person> =
        using(sessionOf(dataSource)) { session ->
            session.transaction { tx ->
                PersonRepository.getAllPersonById(tx, count, startId)
            }
        }

    fun findOrCreatePersonByFnr(
        fnr: Personidentifikator,
        informasjon: String, // Årsak for å lage aktør, hvis nødvendig
    ): Person =
        dataSource.transaction { session ->
            PersonRepository.findPersonByFnr(session, fnr) ?: run {
                val personId = PersonRepository.insert(session, fnr, LocalDate.now(), informasjon)
                if (personId == null) {
                    logger.error { "Kan ikke opprettet person med fnr: xxxx" }
                    throw PersonException("Kan ikke opprettet person med fnr: xxxx")
                }
                PersonRepository.findPersonById(session, PersonId(personId))
            }
        }
}
