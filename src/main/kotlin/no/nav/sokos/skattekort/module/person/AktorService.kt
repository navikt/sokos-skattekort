package no.nav.sokos.skattekort.module.person

import javax.sql.DataSource

import mu.KotlinLogging

import no.nav.sokos.skattekort.config.TEAM_LOGS_MARKER
import no.nav.sokos.skattekort.kafka.PersonHendelseDTO

private val logger = KotlinLogging.logger {}

class AktorService(
    private val dataSource: DataSource,
    private val personService: PersonService,
) {
    suspend fun processIdentChanging(personHendelse: PersonHendelseDTO) {
        logger.info(marker = TEAM_LOGS_MARKER) { "Processing Aktor with identifikatorList: $personHendelse" }
//        dataSource.suspendTransaction { tx ->
//            val personidentifikatorList =
//                identifikatorList
//                    .filter { it.type != IdentType.AKTORID }
//                    .map { it.idnummer }
//            val personIdList = personService.findAllPersonIdByPersonidentifikator(tx, personidentifikatorList)
//        }

//        identifikatorList.forEach { identifikator ->
//
//        }
    }
}
