package no.nav.sokos.skattekort.skattekortdata

import javax.sql.DataSource

import kotlinx.serialization.json.Json

import kotliquery.TransactionalSession
import mu.KotlinLogging

import no.nav.sokos.skattekort.config.TEAM_LOGS_MARKER
import no.nav.sokos.skattekort.forespoersel.AbonnementRepository
import no.nav.sokos.skattekort.forespoersel.Forsystem
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
import no.nav.sokos.skattekort.skattekortbestilling.BestillingsbatchType
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
                val skattekortData = SkattekortDataRepository.getUnprocessedSkattekortData(tx).map { data -> Triple(data.id, data.type, Json.decodeFromString<Arbeidstaker>(data.dataMottatt)) }
                skattekortData.forEach { (id, type, arbeidstaker) ->
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
                    SkattekortDataRepository.updateSkattekortId(tx, id.value, skattekortId.value)
                    opprettUtsendingerForAbonnementer(tx, personId, inntektsaar, type)
                }
            }
        }.onFailure { exception ->
            logger.error(exception) { "Konvertering av skattekort data til skattetkort og opprett utsendinger feilet: ${exception.message}" }
        }
    }

    private fun opprettUtsendingerForAbonnementer(
        tx: TransactionalSession,
        personId: PersonId,
        inntektsaar: Int,
        type: BestillingsbatchType?,
    ) {
        AbonnementRepository.findForsystemAndFnr(tx, personId, inntektsaar).forEach { (system, fnr) ->
            val forsystem =
                when {
                    system == Forsystem.OPPDRAGSSYSTEMET_STOR && type == BestillingsbatchType.OPPDATERING -> Forsystem.OPPDRAGSSYSTEMET
                    else -> system
                }
            UtsendingRepository.insertBatch(
                tx,
                utsendingList =
                    listOf(
                        Utsending(
                            inntektsaar = inntektsaar,
                            fnr = fnr,
                            forsystem = forsystem,
                        ),
                    ),
            )
        }
    }
}
