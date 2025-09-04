package no.nav.sokos.skattekort.aktoer

import javax.sql.DataSource

import kotliquery.sessionOf
import kotliquery.using

class AktoerService(
    val dataSource: DataSource,
    val aktoerRepository: AktoerRepository,
) {
    fun list(
        count: Int = 10,
        startId: String? = null,
    ): List<Aktoer> =
        using(sessionOf(dataSource)) { session ->
            session.transaction { tx ->
                aktoerRepository.list(tx, count, startId)
            }
        }
}
