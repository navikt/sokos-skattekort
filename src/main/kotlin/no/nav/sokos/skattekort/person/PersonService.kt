package no.nav.sokos.skattekort.person

import javax.sql.DataSource

import kotliquery.sessionOf
import kotliquery.using

class PersonService(
    val dataSource: DataSource,
    val personRepository: PersonRepository,
) {
    fun list(
        count: Int = 10,
        startId: String? = null,
    ): List<Aktoer> =
        using(sessionOf(dataSource)) { session ->
            session.transaction { tx ->
                personRepository.list(tx, count, startId)
            }
        }
}
