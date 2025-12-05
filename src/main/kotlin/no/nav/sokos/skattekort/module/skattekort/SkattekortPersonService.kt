package no.nav.sokos.skattekort.module.skattekort

import javax.sql.DataSource

import mu.KotlinLogging

import no.nav.sokos.skattekort.api.skattekortpersonapi.v1.Arbeidstaker
import no.nav.sokos.skattekort.api.skattekortpersonapi.v1.SkattekortPersonRequest
import no.nav.sokos.skattekort.audit.AuditLogg
import no.nav.sokos.skattekort.audit.AuditLogger
import no.nav.sokos.skattekort.audit.Saksbehandler
import no.nav.sokos.skattekort.config.TEAM_LOGS_MARKER
import no.nav.sokos.skattekort.exception.PersonNotFoundException
import no.nav.sokos.skattekort.module.person.PersonRepository
import no.nav.sokos.skattekort.module.person.Personidentifikator
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

            require(skattekortPersonRequest.fnr.all { it.isDigit() }, { "fnr kan bare inneholde siffer, var ${skattekortPersonRequest.fnr}" })
            require(skattekortPersonRequest.fnr.length == 11, { "fnr må ha lengde 11, var ${skattekortPersonRequest.fnr}" })
            require(
                skattekortPersonRequest.inntektsaar in 2025..<2100,
                { "inntektsaar ser ikke ut som et gyldig årstall, var ${skattekortPersonRequest.inntektsaar}" },
            )
            val person =
                PersonRepository.findPersonByFnr(tx, Personidentifikator(skattekortPersonRequest.fnr))
                    ?: throw PersonNotFoundException("Fant ikke person med fnr ${skattekortPersonRequest.fnr}")
            val skattekort: List<Skattekort> =
                SkattekortRepository
                    .findAllByPersonId(
                        tx,
                        person.id!!,
                        skattekortPersonRequest.inntektsaar.toInt(),
                        adminRole = false,
                    )
            skattekort.map {
                Arbeidstaker(
                    skattekortPersonRequest.inntektsaar.toLong(),
                    skattekortPersonRequest.fnr,
                    it,
                )
            }
        }
}
