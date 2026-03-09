package no.nav.sokos.skattekort.infrastructure.pdl

import mu.KotlinLogging

import no.nav.sokos.skattekort.config.TEAM_LOGS_MARKER
import no.nav.sokos.skattekort.infrastructure.tilgangsmaskin.TilgangsmaskinClientService
import no.nav.sokos.skattekort.security.Saksbehandler

private val logger = KotlinLogging.logger {}

class PdlService(
    private val pdlClientService: PdlClientService,
    private val tilgangsmaskinClientService: TilgangsmaskinClientService,
) {
    suspend fun getPersonNavn(
        ident: String,
        saksbehandler: Saksbehandler,
    ): String? {
        logger.info(marker = TEAM_LOGS_MARKER) { "Henter navn for ident: $ident" }

        val problemDetailResponse = tilgangsmaskinClientService.checkSaksbehandlerAccess(saksbehandler.ident, ident)
        problemDetailResponse?.let { throw IllegalAccessException("Saksbehandler ${saksbehandler.ident} har ikke tilgang til person med ident $ident. $problemDetailResponse") }

        val person = pdlClientService.getPersonNavnBulk(listOf(ident))[ident]?.navn?.firstOrNull()
        return person?.let {
            when (person.mellomnavn) {
                null -> "${person.fornavn} ${person.etternavn}"
                else -> "${person.fornavn} ${person.mellomnavn} ${person.etternavn}"
            }
        }
    }
}
