package no.nav.sokos.skattekort.infrastructure.pdl

import io.github.resilience4j.core.functions.Either
import mu.KotlinLogging

import no.nav.sokos.skattekort.config.TEAM_LOGS_MARKER
import no.nav.sokos.skattekort.infrastructure.tilgangsmaskin.TilgangsmaskinClientService
import no.nav.sokos.skattekort.security.Saksbehandler
import no.nav.sokos.skattekort.util.audit.AuditLogg
import no.nav.sokos.skattekort.util.audit.AuditLogger
import no.nav.tilgangsmaskinen.ProblemDetailResponse

private val logger = KotlinLogging.logger {}

class PdlService(
    private val pdlClientService: PdlClientService,
    private val tilgangsmaskinClientService: TilgangsmaskinClientService,
    private val auditLogger: AuditLogger,
) {
    suspend fun getPersonNavn(
        ident: String,
        saksbehandler: Saksbehandler,
    ): Either<ProblemDetailResponse, String> {
        logger.info(marker = TEAM_LOGS_MARKER) { "Henter navn for ident: $ident" }
        auditLogger.auditLog(
            AuditLogg(
                saksbehandler = saksbehandler.ident,
                fnr = ident,
                brukerhandling = "NAV-ansatt har gjort et oppslag på bruker for å hente navn",
            ),
        )

        tilgangsmaskinClientService.checkSaksbehandlerAccess(saksbehandler.ident, ident)?.let { response ->
            return Either.left(response)
        }

        val person = pdlClientService.getPersonNavnBulk(listOf(ident))[ident]?.navn?.firstOrNull()
        val personNavn =
            person?.let {
                when (person.mellomnavn) {
                    null -> "${person.fornavn} ${person.etternavn}"
                    else -> "${person.fornavn} ${person.mellomnavn} ${person.etternavn}"
                }
            } ?: ""
        return Either.right(personNavn)
    }
}
