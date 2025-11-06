package no.nav.sokos.skattekort.module.person

import javax.sql.DataSource

import mu.KotlinLogging

import no.nav.sokos.skattekort.config.TEAM_LOGS_MARKER
import no.nav.sokos.skattekort.kafka.IdentType
import no.nav.sokos.skattekort.kafka.IdentifikatorDTO
import no.nav.sokos.skattekort.util.SQLUtils.suspendTransaction

private val logger = KotlinLogging.logger {}

class AktorService(
    private val dataSource: DataSource,
    private val personService: PersonService,
) {
    suspend fun processIdentChanging(identifikatorList: List<IdentifikatorDTO>) {
        dataSource.suspendTransaction { tx ->
            val foedselsnummerList =
                identifikatorList
                    .filter { it.type != IdentType.AKTORID }
                    .map { it.idnummer }
            if (personService.isPersonExists(tx, foedselsnummerList)) {
            }
        }

        identifikatorList.forEach { identifikator ->
            logger.info(marker = TEAM_LOGS_MARKER) { "Processing Aktor with identifikator: $identifikator" }
        }
    }
}
