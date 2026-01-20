package no.nav.sokos.skattekort.module.skattekort

import javax.sql.DataSource

import mu.KotlinLogging

import no.nav.sokos.skattekort.api.skattekortpersonapi.v1.Arbeidstaker
import no.nav.sokos.skattekort.api.skattekortpersonapi.v1.SkattekortPersonRequest
import no.nav.sokos.skattekort.audit.AuditLogg
import no.nav.sokos.skattekort.audit.AuditLogger
import no.nav.sokos.skattekort.config.TEAM_LOGS_MARKER
import no.nav.sokos.skattekort.module.person.PersonRepository
import no.nav.sokos.skattekort.module.person.Personidentifikator
import no.nav.sokos.skattekort.security.Saksbehandler
import no.nav.sokos.skattekort.util.SQLUtils.transaction

private val logger = KotlinLogging.logger {}

class SkattekortPersonService(
    val dataSource: DataSource,
    private val auditLogger: AuditLogger,
) {
    fun hentSkattekortPerson(
        skattekortPersonRequest: SkattekortPersonRequest,
        saksbehandler: Saksbehandler,
    ): List<Arbeidstaker> =
        dataSource.transaction { tx ->
            logger.info(marker = TEAM_LOGS_MARKER) { "Henter skattekort for person: $skattekortPersonRequest" }
            auditLogger.auditLog(AuditLogg(saksbehandler = saksbehandler.ident, fnr = skattekortPersonRequest.fnr))

            val person = PersonRepository.findPersonByFnr(tx, Personidentifikator(skattekortPersonRequest.fnr)) ?: return@transaction emptyList()

            val skattekort: List<Skattekort> =
                SkattekortRepository
                    .findAllByPersonId(
                        tx,
                        person.id!!,
                        skattekortPersonRequest.inntektsaar.toInt(),
                        adminRole = false,
                    )

            return@transaction when {
                skattekort.isEmpty() -> emptyList()
                else ->
                    skattekort.map { skattekortItem ->
                        Arbeidstaker(
                            skattekortPersonRequest.inntektsaar.toLong(),
                            skattekortPersonRequest.fnr,
                            skattekortItem,
                        )
                    }
            }
        }
}
