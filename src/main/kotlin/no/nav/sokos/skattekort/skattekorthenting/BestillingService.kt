package no.nav.sokos.skattekort.skattekorthenting

import java.time.Duration
import java.time.Instant
import javax.sql.DataSource

import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.toJavaInstant
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json

import io.github.resilience4j.circuitbreaker.CallNotPermittedException
import kotliquery.TransactionalSession
import mu.KotlinLogging

import no.nav.sokos.skattekort.config.RECENT_BATCH_GRACE_PERIOD
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
import no.nav.sokos.skattekort.person.PersonService
import no.nav.sokos.skattekort.person.Personidentifikator
import no.nav.sokos.skattekort.skattekort.ResponseStatus
import no.nav.sokos.skattekort.skattekort.ResultatForSkattekort
import no.nav.sokos.skattekort.skattekortbestilling.BestillingsbatchRepository
import no.nav.sokos.skattekort.skattekortbestilling.BestillingsbatchStatus
import no.nav.sokos.skattekort.skattekortbestilling.BestillingsbatchType
import no.nav.sokos.skattekort.skattekortbestilling.BestillingsbatchType.BESTILLING
import no.nav.sokos.skattekort.skattekortdata.SkattekortDataRepository
import no.nav.sokos.skattekort.util.SQLUtils.transaction

private val logger = KotlinLogging.logger {}

class BestillingService(
    private val dataSource: DataSource,
    private val skatteetatenClient: SkatteetatenClient,
    private val featureToggles: UnleashIntegration,
    private val personService: PersonService,
) {
    private val errorLoggedBatchIds = mutableSetOf<Long>()

    @OptIn(ExperimentalTime::class)
    fun hentBestillingsbatcher(type: BestillingsbatchType) {
        val bestillingsbatchList =
            try {
                dataSource.transaction { tx -> BestillingsbatchRepository.getAllUnprocessedBestillingsbatch(tx, type) }
            } catch (exception: Exception) {
                logger.error(exception) { "Feil ved henting av bestillingsbatcher for type $type" }
                return
            }

        for (batch in bestillingsbatchList) {
            val batchId = batch.id!!.id
            runCatching {
                logger.info("Henter skattekort for ${batch.bestillingsreferanse}")
                val response = runBlocking { skatteetatenClient.hentSkattekort(batch.bestillingsreferanse) }
                if (response == null) {
                    logger.info("Svaret er ikke klart ennå for bestillingsbatch $batchId, forsøker igjen senere")
                    if (batch.opprettet.plus(RECENT_BATCH_GRACE_PERIOD) < Clock.System.now() && errorLoggedBatchIds.add(batchId)) {
                        logger.error { "Henting av skattekort for batch $batchId feilet med tomt svar for mer enn ${RECENT_BATCH_GRACE_PERIOD.inWholeHours} time siden." }
                    }
                    return@runCatching
                }
                errorLoggedBatchIds.remove(batchId)

                logger.info("Ved henting av skattekort for batch $batchId returnerte Skatteetaten ${response.status}")
                handleBestillingsbatch(batchId = batchId, response = response, type = type)
            }.onFailure { exception ->
                when (exception) {
                    is CallNotPermittedException -> {
                        return@onFailure
                    }

                    is UgyldigOrganisasjonsnummerException -> {
                        logger.error(marker = TEAM_LOGS_MARKER, exception) { "Ugydlig organisasjonsnummer av skattekort for batch $batchId feilet. ${exception.message}" }
                        logger.error("Henting av skattekort for batch $batchId feilet med Ugyldig organisasjonsnummer, sjekk TEAM LOGS for detaljer")
                        dataSource.transaction { errorTx ->
                            BestillingsbatchRepository.markAs(errorTx, batchId, BestillingsbatchStatus.FEILET)
                            AuditRepository.insertBatch(
                                errorTx,
                                AuditTag.HENTING_AV_SKATTEKORT_FEILET,
                                BestillingRepository.getAllBestillingsInBatch(errorTx, batchId).map { bestilling -> bestilling.personId },
                                "Batchhenting av skattekort feilet med ${exception.javaClass.simpleName}, batchid $batchId",
                            )
                        }
                    }

                    else -> {
                        if (batch.opprettet
                                .toJavaInstant()
                                .plus(Duration.ofHours(1))
                                .isBefore(Instant.now())
                        ) {
                            logger.error(marker = TEAM_LOGS_MARKER, exception) { "Henting av skattekort for batch $batchId feilet. ${exception.message}" }
                            logger.error("Henting av skattekort for batch: $batchId, type: ${batch.type.name} feilet, sjekk TEAM LOGS for detaljer")
                            dataSource.transaction { errorTx ->
                                BestillingsbatchRepository.markAs(errorTx, batchId, BestillingsbatchStatus.FEILET)
                                if (batch.type == BESTILLING) {
                                    AuditRepository.insertBatch(
                                        errorTx,
                                        AuditTag.HENTING_AV_SKATTEKORT_FEILET,
                                        BestillingRepository.getAllBestillingsInBatch(errorTx, batchId).map { bestilling -> bestilling.personId },
                                        "Batchhenting av skattekort feilet med ${exception.javaClass.simpleName}, batchid $batchId",
                                    )
                                }
                            }
                        } else {
                            logger.info { "Henting av skattekort for batch: $batchId, type: ${batch.type.name} feilet, men prøvd på nytt senere" }
                            dataSource.transaction { tx -> BestillingsbatchRepository.markAs(tx, batchId, BestillingsbatchStatus.RETRY) }
                        }
                        break
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
                BestillingsbatchRepository.updateBestillingsbatchWithMottatteData(tx, batchId, Json.encodeToString(response))
            }
            when (response.status) {
                ResponseStatus.FORESPOERSEL_OK.name -> {
                    val arbeidstakerList = response.arbeidsgiver?.first()?.arbeidstaker ?: emptyList()
                    arbeidstakerList.forEach { arbeidstaker ->
                        val personId =
                            PersonRepository.findPersonIdByFnr(tx, Personidentifikator(arbeidstaker.arbeidstakeridentifikator)) ?: run {
                                logger.error(marker = TEAM_LOGS_MARKER) { "Fant ikke person for fnr ${arbeidstaker.arbeidstakeridentifikator}" }
                                logger.error { "Fant ikke person, sjekk TEAM_LOGS for detaljer" }
                                return@forEach
                            }
                        handleResultatForSkattekort(tx, arbeidstaker, personId)
                    }

                    if (type == BESTILLING) {
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
                        bestillingerMottattCounter.inc(arbeidstakerList.size.toLong())
                    } else {
                        oppdateringerMottattCounter.inc(arbeidstakerList.size.toLong())
                    }
                    BestillingsbatchRepository.markAs(tx, batchId, BestillingsbatchStatus.FERDIG)
                    logger.info("Bestillingsbatch $batchId ferdig behandlet med mottatte brukere")
                }

                ResponseStatus.INGEN_ENDRINGER.name -> {
                    BestillingsbatchRepository.markAs(tx, batchId, BestillingsbatchStatus.FERDIG)
                    logger.info("Ingen oppdaterte skattekort på batch $batchId")
                }

                else -> {
                    logger.error { "Bestillingsbatch $batchId feilet: ${response.status}" }
                    logger.error(TEAM_LOGS_MARKER) { "Batchhenting av skattekort avvist av Skatteetaten: $response" }
                    BestillingsbatchRepository.markAs(tx, batchId, BestillingsbatchStatus.FEILET)
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
        personId: PersonId,
    ) {
        val inntektsaar = arbeidstaker.inntektsaar
        when (ResultatForSkattekort.fromValue(arbeidstaker.resultatForSkattekort)) {
            ResultatForSkattekort.IkkeSkattekort, ResultatForSkattekort.IkkeTrekkplikt, ResultatForSkattekort.SkattekortopplysningerOK -> {
                SkattekortDataRepository.insert(tx, Json.encodeToString(arbeidstaker), inntektsaar, arbeidstaker.arbeidstakeridentifikator)
                AuditRepository.insert(tx, AuditTag.SKATTEKORTINFORMASJON_MOTTATT, personId, "Mottatt skattekortinformasjon for inntektsår $inntektsaar")
            }

            ResultatForSkattekort.UtgaattDnummerSkattekortForFoedselsnummerErLevert -> {
                val gyldigFnr = personService.getGyldigFnr(arbeidstaker.arbeidstakeridentifikator, personId)
                val personIdentifikator = requireNotNull(gyldigFnr) { "Har ikke fått nytt fnr for personId $personId" }.fnr

                BestillingRepository.insert(tx, Bestilling(personId = personId, fnr = personIdentifikator, inntektsaar = inntektsaar))
                AuditRepository.insert(tx, AuditTag.NYTT_FNR, personId, "Opprettet bestilling pga. tilbakemelding fra Skatteetaten om utgått Personidentifikator")
            }

            ResultatForSkattekort.UgyldigOrganisasjonsnummer -> {
                throw UgyldigOrganisasjonsnummerException("Ugyldig organisasjonsnummer")
            }

            ResultatForSkattekort.UgyldigFoedselsEllerDnummer -> {
                PersonRepository.flaggPerson(tx, personId)
                SkattekortDataRepository.insert(tx, Json.encodeToString(arbeidstaker), inntektsaar, arbeidstaker.arbeidstakeridentifikator)
                AuditRepository.insert(tx, AuditTag.INVALID_FNR, personId, "Tilbakemelding fra Skatteetaten om UgyldigFoedselsEllerDnummer")
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
