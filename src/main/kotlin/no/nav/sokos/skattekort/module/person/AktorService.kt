package no.nav.sokos.skattekort.module.person

import javax.sql.DataSource

import mu.KotlinLogging

import no.nav.sokos.skattekort.config.TEAM_LOGS_MARKER
import no.nav.sokos.skattekort.kafka.IdentifikatorDTO

private val logger = KotlinLogging.logger {}

class AktorService(
    private val dataSource: DataSource,
    private val personService: PersonService,
) {
    fun processIdentChanging(identifikatorList: List<IdentifikatorDTO>) {
        identifikatorList.forEach { identifikator ->
            logger.info(marker = TEAM_LOGS_MARKER) { "Processing Aktor with identifikator: $identifikator" }
        }
    }
}
