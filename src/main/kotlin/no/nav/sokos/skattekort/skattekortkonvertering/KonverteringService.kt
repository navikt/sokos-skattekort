package no.nav.sokos.skattekort.skattekortkonvertering

import javax.sql.DataSource

import kotlin.time.ExperimentalTime

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
import no.nav.sokos.skattekort.skattekort.ResultatForSkattekort.IkkeSkattekort
import no.nav.sokos.skattekort.skattekort.ResultatForSkattekort.IkkeTrekkplikt
import no.nav.sokos.skattekort.skattekort.ResultatForSkattekort.SkattekortopplysningerOK
import no.nav.sokos.skattekort.skattekort.ResultatForSkattekort.UgyldigFoedselsEllerDnummer
import no.nav.sokos.skattekort.skattekort.ResultatForSkattekort.UgyldigOrganisasjonsnummer
import no.nav.sokos.skattekort.skattekort.ResultatForSkattekort.UtgaattDnummerSkattekortForFoedselsnummerErLevert
import no.nav.sokos.skattekort.skattekort.Skattekort
import no.nav.sokos.skattekort.skattekort.SkattekortId
import no.nav.sokos.skattekort.skattekort.SkattekortRepository
import no.nav.sokos.skattekort.skattekort.Syntetisering
import no.nav.sokos.skattekort.skattekort.UgyldigOrganisasjonsnummerException
import no.nav.sokos.skattekort.util.SQLUtils.transaction
import no.nav.sokos.skattekort.utsending.Utsending
import no.nav.sokos.skattekort.utsending.UtsendingRepository

private val logger = KotlinLogging.logger {}

class KonverteringService(
    private val dataSource: DataSource,
    private val skattekortDataRepository: SkattekortDataRepository,
) {
    fun processRawSkattekort() {
        dataSource.transaction { tx ->
            for (skattekortdata in skattekortDataRepository.getUnprocessedSkattekortData(tx)) {
                handleNyttSkattekort(tx, skattekortdata.arbeidstaker, skattekortdata.bestillingsbatchId)
            }
        }
    }

    private fun handleNyttSkattekort(
        tx: TransactionalSession,
        arbeidstaker: Arbeidstaker,
        batchId: String,
    ) {
        val personId =
            PersonRepository.findPersonIdByFnr(tx, Personidentifikator(arbeidstaker.arbeidstakeridentifikator)) ?: run {
                logger.error(marker = TEAM_LOGS_MARKER) { "Fant ikke person for fnr ${arbeidstaker.arbeidstakeridentifikator}" }
                return
            }

        val inntektsaar = arbeidstaker.inntektsaar

        val skattekort = Skattekort(personId, arbeidstaker)
        val id = SkattekortId(SkattekortRepository.insert(tx, skattekort, batchId))

        when (skattekort.resultatForSkattekort) {
            IkkeSkattekort, IkkeTrekkplikt, SkattekortopplysningerOK -> {
                Syntetisering.evtSyntetiserSkattekort(skattekort, id)?.let { (syntetisertSkattekort, aarsak) ->
                    SkattekortRepository.insert(tx, syntetisertSkattekort, "syntetisk")
                    AuditRepository.insert(tx, AuditTag.SYNTETISERT_SKATTEKORT, personId, aarsak)
                }
                opprettUtsendingerForAbonnementer(tx, personId, inntektsaar)
            }

            UtgaattDnummerSkattekortForFoedselsnummerErLevert -> {
                val gyldigFnr = PersonRepository.findGyldigFnrByPersonId(tx, personId)!!
                check(gyldigFnr.value != arbeidstaker.arbeidstakeridentifikator) { "Har ikke fått nytt fnr for personId $personId" }
                BestillingRepository.insert(
                    tx,
                    Bestilling(
                        personId = personId,
                        fnr = gyldigFnr,
                        inntektsaar = inntektsaar,
                    ),
                )
                AuditRepository.insert(tx, AuditTag.NYTT_FNR, personId, "Opprettet bestilling pga. tilbakemelding fra Skatteetaten om utgått Personidentifikator")
            }

            UgyldigOrganisasjonsnummer -> {
                throw UgyldigOrganisasjonsnummerException("Ugyldig organisasjonsnummer")
            }

            UgyldigFoedselsEllerDnummer -> {
                PersonRepository.flaggPerson(tx, personId)
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
