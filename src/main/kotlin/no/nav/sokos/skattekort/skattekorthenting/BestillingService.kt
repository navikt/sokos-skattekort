package no.nav.sokos.skattekort.skattekorthenting

import javax.sql.DataSource

import kotlin.time.ExperimentalTime
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json

import io.github.resilience4j.circuitbreaker.CallNotPermittedException
import kotliquery.TransactionalSession
import mu.KotlinLogging

import no.nav.sokos.skattekort.config.TEAM_LOGS_MARKER
import no.nav.sokos.skattekort.infrastructure.Metrics.counter
import no.nav.sokos.skattekort.infrastructure.UnleashIntegration
import no.nav.sokos.skattekort.infrastructure.skatteetaten.SkatteetatenClient
import no.nav.sokos.skattekort.infrastructure.skatteetaten.hentskattekort.Arbeidstaker
import no.nav.sokos.skattekort.infrastructure.skatteetaten.hentskattekort.HentSkattekortResponse
import no.nav.sokos.skattekort.person.AuditRepository
import no.nav.sokos.skattekort.person.AuditTag
import no.nav.sokos.skattekort.person.PersonId
import no.nav.sokos.skattekort.person.PersonRepository
import no.nav.sokos.skattekort.person.Personidentifikator
import no.nav.sokos.skattekort.skattekort.ResponseStatus
import no.nav.sokos.skattekort.skattekort.ResultatForSkattekort
import no.nav.sokos.skattekort.skattekort.UgyldigOrganisasjonsnummerException
import no.nav.sokos.skattekort.skattekortbestilling.BestillingBatchRepository
import no.nav.sokos.skattekort.skattekortbestilling.BestillingBatchStatus
import no.nav.sokos.skattekort.skattekortbestilling.BestillingsbatchType
import no.nav.sokos.skattekort.skattekortbestilling.BestillingsbatchType.BESTILLING
import no.nav.sokos.skattekort.skattekortkonvertering.SkattekortDataRepository
import no.nav.sokos.skattekort.util.SQLUtils.transaction

private val logger = KotlinLogging.logger {}

class BestillingService(
    private val dataSource: DataSource,
    private val skatteetatenClient: SkatteetatenClient,
    private val featureToggles: UnleashIntegration,
) {
    @OptIn(ExperimentalTime::class)
    fun hentBestillingsbatcher(type: BestillingsbatchType) {
        /* denne er med vilje ikke underlagt feature switch-styring for å unngå at en sendt bestilling
        ikke timer ut mens feature-toggelen er slått av
         */
        dataSource.transaction { tx -> BestillingBatchRepository.getUnprocessedBestillingsbatchList(tx, type) }.forEach { batch ->
            val batchId = batch.id!!.id
            runCatching {
                logger.info("Henter skattekort for ${batch.bestillingsreferanse}")
                val response =
                    runBlocking {
                        skatteetatenClient.hentSkattekort(batch.bestillingsreferanse)
                    } ?: run {
                        logger.info("Svaret er ikke klart ennå for bestillingsbatch $batchId, forsøker igjen senere")
                        return@runCatching
                    }
                logger.info("Ved henting av skattekort for batch $batchId returnerte Skatteetaten ${response.status}")
                handleBestillingsbatch(batchId = batchId, response = response, type = type)
            }.onFailure { exception ->
                when (exception) {
                    is CallNotPermittedException -> return@onFailure
                    else -> {
                        logger.error(marker = TEAM_LOGS_MARKER, exception) { "Henting av skattekort for batch $batchId feilet: ${exception.message}" }
                        logger.error("Henting av skattekort for batch $batchId feilet")
                        dataSource.transaction { errorTx ->
                            BestillingBatchRepository.markAs(errorTx, batchId, BestillingBatchStatus.Feilet)
                            if (exception is UgyldigOrganisasjonsnummerException) BestillingRepository.retryUnprocessedBestillings(errorTx, batchId)
                            AuditRepository.insertBatch(
                                errorTx,
                                AuditTag.HENTING_AV_SKATTEKORT_FEILET,
                                BestillingRepository.getAllBestillingsInBatch(errorTx, batchId).map { bestilling -> bestilling.personId },
                                "Batchhenting av skattekort feilet med ${exception.javaClass.simpleName}, batchid $batchId",
                            )
                        }
                    }
                }
            }
        }
    }

    private fun handleBestillingsbatch(
        batchId: Long,
        response: HentSkattekortResponse,
        type: BestillingsbatchType,
    ) {
        dataSource.transaction { tx ->
            if (featureToggles.isLagreMottatteBestillingerEnabled()) {
                BestillingBatchRepository.updateBestillingsbatchWithMottatteData(tx, batchId, Json.encodeToString(response))
            }
            when (response.status) {
                ResponseStatus.FORESPOERSEL_OK.name -> {
                    val arbeidstakerList = response.arbeidsgiver?.first()?.arbeidstaker ?: emptyList()
                    arbeidstakerList.forEach { arbeidstaker ->
                        handleResultatForSkattekort(tx, arbeidstaker)
                    }
                    if (type == BESTILLING) {
                        bestillingerMottattCounter.inc(arbeidstakerList.size.toLong())
                        BestillingRepository.deleteProcessedBestillingBatch(tx, arbeidstakerList.map { it.arbeidstakeridentifikator }, batchId)

                        val personer: List<PersonId> = BestillingRepository.hentResterendeBestillinger(tx, batchId)
                        if (personer.isNotEmpty()) {
                            AuditRepository.insertBatch(
                                tx = tx,
                                tag = AuditTag.BESTILLING_ETTERLATT,
                                personIds = personer,
                                informasjon = "Bestilling var etterlatt etter mottak av data i batch $batchId",
                            )
                        }
                        BestillingRepository.retryUnprocessedBestillings(tx, batchId)
                        BestillingBatchRepository.markAs(tx, batchId, BestillingBatchStatus.Ferdig)
                        logger.info("Bestillingsbatch $batchId ferdig behandlet med mottatte brukere")
                    } else {
                        oppdateringerMottattCounter.inc(arbeidstakerList.size.toLong())
                    }
                }

                ResponseStatus.INGEN_ENDRINGER.name -> {
                    BestillingBatchRepository.markAs(tx, batchId, BestillingBatchStatus.Ferdig)
                    logger.info("Ingen oppdaterte skattekort på batch $batchId")
                }

                else -> {
                    logger.error { "Bestillingsbatch $batchId feilet: ${response.status}" }
                    logger.error(TEAM_LOGS_MARKER) { "Batchhenting av skattekort avvist av Skatteetaten: $response" }
                    BestillingBatchRepository.markAs(tx, batchId, BestillingBatchStatus.Feilet)
                    AuditRepository.insertBatch(
                        tx,
                        AuditTag.HENTING_AV_SKATTEKORT_FEILET,
                        BestillingRepository.getAllBestillingsInBatch(tx, batchId).map { bestilling -> bestilling.personId },
                        "Batchhenting av skattekort avvist av Skatteetaten med status: ${response.status}",
                    )
                    return@transaction
                }
            }
        }
    }

    private fun handleResultatForSkattekort(
        tx: TransactionalSession,
        arbeidstaker: Arbeidstaker,
    ) {
        val personId =
            PersonRepository.findPersonIdByFnr(tx, Personidentifikator(arbeidstaker.arbeidstakeridentifikator)) ?: run {
                logger.error(marker = TEAM_LOGS_MARKER) { "Fant ikke person for fnr ${arbeidstaker.arbeidstakeridentifikator}" }
                return
            }
        val inntektsaar = arbeidstaker.inntektsaar
        when (ResultatForSkattekort.fromValue(arbeidstaker.resultatForSkattekort)) {
            ResultatForSkattekort.IkkeSkattekort, ResultatForSkattekort.IkkeTrekkplikt, ResultatForSkattekort.SkattekortopplysningerOK -> {
                SkattekortDataRepository.insert(tx, Json.encodeToString(arbeidstaker), inntektsaar, arbeidstaker.arbeidstakeridentifikator)
                AuditRepository.insert(tx, AuditTag.SKATTEKORTINFORMASJON_MOTTATT, personId, "Mottatt skattekortinformasjon for inntektsår $inntektsaar")
            }

            ResultatForSkattekort.UtgaattDnummerSkattekortForFoedselsnummerErLevert -> {
                val gyldigFnr = PersonRepository.findGyldigFnrByPersonId(tx, personId)!!
                check(gyldigFnr.value != arbeidstaker.arbeidstakeridentifikator) { "Har ikke fått nytt fnr for personId $personId" }
                BestillingRepository.insert(tx, Bestilling(personId = personId, fnr = gyldigFnr, inntektsaar = inntektsaar))
                AuditRepository.insert(tx, AuditTag.NYTT_FNR, personId, "Opprettet bestilling pga. tilbakemelding fra Skatteetaten om utgått Personidentifikator")
            }

            ResultatForSkattekort.UgyldigOrganisasjonsnummer -> {
                throw UgyldigOrganisasjonsnummerException("Ugyldig organisasjonsnummer")
            }

            ResultatForSkattekort.UgyldigFoedselsEllerDnummer -> {
                PersonRepository.flaggPerson(tx, personId)
                SkattekortDataRepository.insert(tx, Json.encodeToString(arbeidstaker), inntektsaar, arbeidstaker.arbeidstakeridentifikator)
            }
        }
    }

    companion object {
        val oppdateringerMottattCounter =
            counter(
                name = "oppdaterte_skattekort",
                helpText = "Mottatte oppdateringer av skattekort",
            )
        val bestillingerMottattCounter =
            counter(
                name = "bestilte_skattekort",
                helpText = "Mottatte bestilte skattekort",
            )
    }
}
