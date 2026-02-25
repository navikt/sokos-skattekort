package no.nav.sokos.skattekort.skattekortkonvertering

import javax.sql.DataSource
import kotlin.time.ExperimentalTime
import kotlinx.serialization.json.Json
import kotliquery.TransactionalSession
import mu.KotlinLogging
import no.nav.sokos.skattekort.config.TEAM_LOGS_MARKER
import no.nav.sokos.skattekort.forespoersel.AbonnementRepository
import no.nav.sokos.skattekort.infrastructure.skatteetaten.hentskattekort.Arbeidstaker
import no.nav.sokos.skattekort.person.*
import no.nav.sokos.skattekort.person.AuditRepository
import no.nav.sokos.skattekort.person.AuditTag
import no.nav.sokos.skattekort.person.PersonRepository
import no.nav.sokos.skattekort.person.Personidentifikator
import no.nav.sokos.skattekort.skattekort.Skattekort
import no.nav.sokos.skattekort.skattekort.SkattekortId
import no.nav.sokos.skattekort.skattekort.SkattekortRepository
import no.nav.sokos.skattekort.skattekort.Syntetisering
import no.nav.sokos.skattekort.util.SQLUtils.transaction
import no.nav.sokos.skattekort.utsending.Utsending
import no.nav.sokos.skattekort.utsending.UtsendingRepository

private val logger = KotlinLogging.logger {}

class KonverteringService(
    private val dataSource: DataSource,
    private val skattekortDataRepository: SkattekortDataRepository,
) {
    fun processSkattekortData() {
        dataSource.transaction { tx ->
            val skattekortData = skattekortDataRepository.getUnprocessedSkattekortData(tx).map { Json.decodeFromString<Arbeidstaker>(it) }
            skattekortData.forEach { arbeidstaker ->
                val personId =
                    PersonRepository.findPersonIdByFnr(tx, Personidentifikator(arbeidstaker.arbeidstakeridentifikator)) ?: this.run {
                        logger.error(marker = TEAM_LOGS_MARKER) { "Fant ikke person for fnr ${arbeidstaker.arbeidstakeridentifikator}" }
                        return@transaction
                    }
                val inntektsaar = arbeidstaker.inntektsaar
                val skattekort = Skattekort(personId, arbeidstaker)
                val id = SkattekortId(SkattekortRepository.insert(tx, skattekort))
                Syntetisering.evtSyntetiserSkattekort(skattekort, id)?.let { (syntetisertSkattekort, aarsak) ->
                    SkattekortRepository.insert(tx, syntetisertSkattekort)
                    AuditRepository.insert(tx, AuditTag.SYNTETISERT_SKATTEKORT, personId, aarsak)
                }
                opprettUtsendingerForAbonnementer(tx, personId, inntektsaar)
            }
        }
    }

    @OptIn(ExperimentalTime::class)
    private fun opprettUtsendingerForAbonnementer(
        tx: TransactionalSession,
        personId: PersonId,
        inntektsaar: Int,
    ) {
        AbonnementRepository.findForsystemAndFnr(tx, personId, inntektsaar).forEach { (forsystem, fnr) ->
            UtsendingRepository.insert(
                tx,
                Utsending(
                    inntektsaar = inntektsaar,
                    fnr = fnr,
                    forsystem = forsystem,
                ),
            )
        }
    }
}
