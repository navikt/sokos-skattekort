package no.nav.sokos.skattekort.skattekortdata

import javax.sql.DataSource

import kotlin.time.ExperimentalTime
import kotlinx.serialization.json.Json

import kotliquery.TransactionalSession
import mu.KotlinLogging

import no.nav.sokos.skattekort.config.TEAM_LOGS_MARKER
import no.nav.sokos.skattekort.forespoersel.AbonnementRepository
import no.nav.sokos.skattekort.infrastructure.skatteetaten.hentskattekort.Arbeidstaker
import no.nav.sokos.skattekort.person.AuditRepository
import no.nav.sokos.skattekort.person.AuditTag
import no.nav.sokos.skattekort.person.PersonId
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

class SkattekortDataService(
    private val dataSource: DataSource,
) {
    fun processSkattekortData() {
        runCatching {
            dataSource.transaction { tx ->
                val skattekortData = SkattekortDataRepository.getUnprocessedSkattekortData(tx).map { data -> Pair(data.first, Json.decodeFromString<Arbeidstaker>(data.second)) }
                skattekortData.forEach { (id, arbeidstaker) ->
                    val personId =
                        PersonRepository.findPersonIdByFnr(tx, Personidentifikator(arbeidstaker.arbeidstakeridentifikator)) ?: this.run {
                            logger.error(marker = TEAM_LOGS_MARKER) { "Fant ikke person for fnr ${arbeidstaker.arbeidstakeridentifikator}" }
                            return@transaction
                        }
                    val inntektsaar = arbeidstaker.inntektsaar
                    val skattekort = Skattekort(personId, arbeidstaker)
                    val skattekortId = SkattekortId(SkattekortRepository.insert(tx, skattekort))
                    Syntetisering.evtSyntetiserSkattekort(skattekort, skattekortId)?.let { (syntetisertSkattekort, aarsak) ->
                        SkattekortRepository.insert(tx, syntetisertSkattekort)
                        AuditRepository.insert(tx, AuditTag.SYNTETISERT_SKATTEKORT, personId, aarsak)
                    }
                    SkattekortDataRepository.updateSkattekortId(tx, id, skattekortId.value)
                    opprettUtsendingerForAbonnementer(tx, personId, inntektsaar)
                }
            }
        }.onFailure { exception ->
            logger.error(exception) { "Konvertering av skattekort data til skattetkort og opprett utsendinger feilet: ${exception.message}" }
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
