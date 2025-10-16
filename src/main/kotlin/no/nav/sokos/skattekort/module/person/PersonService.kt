package no.nav.sokos.skattekort.module.person

import java.time.LocalDate

import com.zaxxer.hikari.HikariDataSource
import kotliquery.TransactionalSession
import mu.KotlinLogging

import no.nav.sokos.skattekort.config.TEAM_LOGS_MARKER
import no.nav.sokos.skattekort.util.SQLUtils.withTx

private val logger = KotlinLogging.logger { }

class PersonService(
    private val dataSource: HikariDataSource,
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
        informasjon: String?,
        tx: TransactionalSession,
    ): Person =
        PersonRepository.findPersonByFnr(tx, fnr)?.let { person ->
            informasjon?.let {
                AuditRepository.insert(tx, AuditTag.MOTTATT_FORESPOERSEL, person.id!!, informasjon)
            }
            person
        } ?: run {
            val personId =
                PersonRepository.insert(tx, fnr, LocalDate.now(), informasjon ?: "")
                    ?: run {
                        logger.error(marker = TEAM_LOGS_MARKER) { "Kan ikke opprettet person med fnr: $fnr" }
                        throw PersonException("Kan ikke opprettet person med fnr: xxxx")
                    }
            logger.info(marker = TEAM_LOGS_MARKER) { "Opprett person fnr: $fnr" }
            PersonRepository.findPersonById(tx, PersonId(personId))
        }
}
