package no.nav.sokos.skattekort.skattekorthenting

import java.sql.BatchUpdateException
import javax.sql.DataSource

import kotlin.time.ExperimentalTime
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json

import io.github.resilience4j.circuitbreaker.CallNotPermittedException
import kotliquery.TransactionalSession
import mu.KotlinLogging
import org.postgresql.util.PSQLException

import no.nav.sokos.skattekort.config.TEAM_LOGS_MARKER
import no.nav.sokos.skattekort.infrastructure.Metrics.counter
import no.nav.sokos.skattekort.infrastructure.UnleashIntegration
import no.nav.sokos.skattekort.infrastructure.skatteetaten.SkatteetatenClient
import no.nav.sokos.skattekort.infrastructure.skatteetaten.hentskattekort.Arbeidstaker
import no.nav.sokos.skattekort.person.*
import no.nav.sokos.skattekort.skattekort.ResponseStatus
import no.nav.sokos.skattekort.skattekort.ResultatForSkattekort
import no.nav.sokos.skattekort.skattekort.ResultatForSkattekort.*
import no.nav.sokos.skattekort.skattekort.UgyldigFoedselsEllerDnummerException
import no.nav.sokos.skattekort.skattekort.UgyldigOrganisasjonsnummerException
import no.nav.sokos.skattekort.skattekortbestilling.BestillingBatch
import no.nav.sokos.skattekort.skattekortbestilling.BestillingBatchRepository
import no.nav.sokos.skattekort.skattekortbestilling.BestillingBatchService
import no.nav.sokos.skattekort.skattekortbestilling.BestillingBatchStatus
import no.nav.sokos.skattekort.skattekortkonvertering.SkattekortDataRepository
import no.nav.sokos.skattekort.util.SQLUtils.transaction

private val logger = KotlinLogging.logger {}

class BestillingService(
    private val dataSource: DataSource,
    private val skatteetatenClient: SkatteetatenClient,
    private val featureToggles: UnleashIntegration,
    private val skattekortDataRepository: SkattekortDataRepository,
    private val bestillingBatchService: BestillingBatchService,
) {
    @OptIn(ExperimentalTime::class)
    fun hentSkattekort() {
        /* denne er med vilje ikke underlagt feature switch-styring for å unngå at en sendt bestilling
        ikke timer ut mens feature-toggelen er slått av
         */
        dataSource.transaction { tx -> BestillingBatchRepository.getUnprocessedBestillingsBatches(tx) }.forEach(::hentEnBatch)
    }

    fun hentEnBatch(bestillingsbatch: BestillingBatch) {
        dataSource.transaction { tx ->
            val batchId = bestillingsbatch.id!!.id
            logger.info("Henter skattekort for ${bestillingsbatch.bestillingsreferanse}")
            runBlocking {
                try {
                    val response = skatteetatenClient.hentSkattekort(tx, bestillingsbatch.bestillingsreferanse)
                    if (response == null) {
                        logger.info("Svaret er ikke klart ennå for bestillingsbatch $batchId, forsøker igjen senere")
                        return@runBlocking
                    }
                    logger.info("Ved henting av skattekort for batch $batchId returnerte Skatteetaten ${response.status}")
                    when (response.status) {
                        ResponseStatus.FORESPOERSEL_OK.name -> {
                            response.arbeidsgiver?.first()?.arbeidstaker?.forEach { arbeidstaker ->
                                ventLitt(tx, arbeidstaker)
                                BestillingRepository.deleteProcessedBestilling(tx, batchId, arbeidstaker.arbeidstakeridentifikator)
                            }

                            val personer: List<PersonId> = BestillingRepository.hentResterendeBestillinger(tx, batchId)
                            AuditRepository.insertBatch(
                                tx = tx,
                                tag = AuditTag.BESTILLING_ETTERLATT,
                                personIds = personer,
                                informasjon = "Bestilling var etterlatt etter mottak av data i batch $batchId",
                            )
                            BestillingRepository.retryUnprocessedBestillings(tx, batchId)
                            BestillingBatchRepository.markAs(tx, batchId, BestillingBatchStatus.Ferdig)
                            logger.info("Bestillingsbatch $batchId ferdig behandlet med mottatte brukere")
                        }

                        ResponseStatus.UGYLDIG_INNTEKTSAAR.name -> {
                            BestillingBatchRepository.markAs(tx, batchId, BestillingBatchStatus.Feilet)
                            logger.error(
                                "Bestillingsbatch $batchId feilet med UGYLDIG_INNTEKTSAAR. Dette skulle ikke ha skjedd, og batchen må opprettes på nytt. Bestillingene har blitt tatt vare på for å muliggjøre manuell håndtering",
                            )
                        }

                        ResponseStatus.INGEN_ENDRINGER.name -> {
                            BestillingBatchRepository.markAs(tx, batchId, BestillingBatchStatus.Ferdig)
                            BestillingRepository.retryUnprocessedBestillings(tx, batchId)
                            logger.debug("Bestillingsbatch $batchId ferdig behandlet uten endringer")
                        }

                        else -> {
                            logger.error { "Bestillingsbatch $batchId feilet: ${response.status}" }
                            BestillingBatchRepository.markAs(tx, batchId, BestillingBatchStatus.Feilet)
                            AuditRepository.insertBatch(
                                tx,
                                AuditTag.HENTING_AV_SKATTEKORT_FEILET,
                                BestillingRepository.getAllBestillingsInBatch(tx, batchId).map { bestilling -> bestilling.personId },
                                "Batchhenting av skattekort avvist av Skatteetaten med status: ${response.status}",
                            )
                        }
                    }
                } catch (ugyldigOrgnummerEx: UgyldigOrganisasjonsnummerException) {
                    dataSource.transaction { errorTx ->
                        logger.error(ugyldigOrgnummerEx) { "Henting av skattekort for batch $batchId feilet: ${ugyldigOrgnummerEx.message}" }
                        BestillingBatchRepository.markAs(errorTx, batchId, BestillingBatchStatus.Feilet)
                        BestillingRepository.updateBestillingsWithBatchId(
                            errorTx,
                            BestillingRepository.getAllBestillingsInBatch(tx, batchId).map { it.id!!.id },
                            null,
                        )
                        AuditRepository.insertBatch(
                            errorTx,
                            AuditTag.HENTING_AV_SKATTEKORT_FEILET,
                            BestillingRepository.getAllBestillingsInBatch(tx, batchId).map { bestilling -> bestilling.personId },
                            "Batchhenting av skattekort feilet pga. ugyldig organisasjonsnummer",
                        )
                    }
                    throw ugyldigOrgnummerEx
                } catch (e: BatchUpdateException) {
                    logger.error(marker = TEAM_LOGS_MARKER, e) { "Henting av skattekort for batch $batchId feilet: ${e.message}" }
                    logger.error("Henting av skattekort for batch $batchId feilet, detaljer er logget til secure log")
                    dataSource.transaction { errorTx ->
                        BestillingBatchRepository.markAs(errorTx, batchId, BestillingBatchStatus.Feilet)
                        AuditRepository.insertBatch(
                            errorTx,
                            AuditTag.HENTING_AV_SKATTEKORT_FEILET,
                            BestillingRepository.getAllBestillingsInBatch(tx, batchId).map { bestilling -> bestilling.personId },
                            "Batchhenting av skattekort feilet med BatchUpdateException, batchid $batchId",
                        )
                    }
                    throw e
                } catch (ex: PSQLException) {
                    if ((ex.message?.contains("could not serialize access due to read/write dependencies") ?: false)) {
                        // En annen transaksjon forsøkte å aksessere samme rader, forsøk igjen senere
                    } else {
                        dataSource.transaction { errorTx ->
                            logger.error(ex) { "Henting av skattekort for batch $batchId feilet: ${ex.message}" }
                            BestillingBatchRepository.markAs(errorTx, batchId, BestillingBatchStatus.Feilet)
                            AuditRepository.insertBatch(
                                errorTx,
                                AuditTag.HENTING_AV_SKATTEKORT_FEILET,
                                BestillingRepository.getAllBestillingsInBatch(tx, batchId).map { bestilling -> bestilling.personId },
                                "Batchhenting av skattekort feilet med ${ex.javaClass.simpleName}, batchid $batchId",
                            )
                        }
                    }
                    throw ex
                } catch (cnpe: CallNotPermittedException) {
                    // Her venter vi med å bestille fordi vi tror det er et forbigående problem med kommunikasjon mot skatteetaten
                    throw cnpe
                } catch (ex: Exception) {
                    dataSource.transaction { errorTx ->
                        logger.error(ex) { "Henting av skattekort for batch $batchId feilet: ${ex.message}" }
                        BestillingBatchRepository.markAs(errorTx, batchId, BestillingBatchStatus.Feilet)
                        AuditRepository.insertBatch(
                            errorTx,
                            AuditTag.HENTING_AV_SKATTEKORT_FEILET,
                            BestillingRepository.getAllBestillingsInBatch(tx, batchId).map { bestilling -> bestilling.personId },
                            "Batchhenting av skattekort feilet med ${ex.javaClass.simpleName}, batchid $batchId",
                        )
                    }
                    throw ex
                }
            }
        }
    }

    fun hentOppdaterteSkattekort() {
        if (featureToggles.isOppdateringEnabled()) {
            dataSource.transaction { tx ->
                val oppdateringsbatch = BestillingBatchRepository.getUnprocessedOppdateringsBatch(tx)
                if (oppdateringsbatch != null) {
                    haandterOppdateringsbestilling(tx, oppdateringsbatch)
                } else {
                    // Fant ikke noen eksisterende batch, gå og lag ny
                    bestillingBatchService.bestillOppdateringer(tx)
                }
            }
        } else {
            logger.debug("Bestillinger er disablet")
        }
    }

    private fun ventLitt(
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
            IkkeSkattekort, IkkeTrekkplikt, SkattekortopplysningerOK -> {
                skattekortDataRepository.insert(tx, Json.encodeToString(arbeidstaker), arbeidstaker.inntektsaar, arbeidstaker.arbeidstakeridentifikator)
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
                throw UgyldigFoedselsEllerDnummerException("Ugyldig organisasjonsnummer")
            }
        }
    }

    private fun haandterOppdateringsbestilling(
        tx: TransactionalSession,
        oppdateringsbatch: BestillingBatch,
    ): Any {
        val batchId = oppdateringsbatch.id!!.id
        logger.info("Henter oppdaterte skattekort for ${oppdateringsbatch.bestillingsreferanse}")
        return runBlocking {
            try {
                val response = skatteetatenClient.hentSkattekort(tx, oppdateringsbatch.bestillingsreferanse)
                if (response != null) {
                    logger.info("Ved henting av skattekort for oppdateringsbatch $batchId returnerte Skatteetaten ${response.status}")
                    when (response.status) {
                        ResponseStatus.FORESPOERSEL_OK.name -> {
                            val arbeidstakere = response.arbeidsgiver!!.first().arbeidstaker
                            oppdateringerMottattCounter.inc(arbeidstakere.size.toLong())
                            arbeidstakere.forEach { arbeidstaker ->
                                skattekortDataRepository.insert(tx, Json.encodeToString(arbeidstaker), arbeidstaker.inntektsaar, arbeidstaker.arbeidstakeridentifikator)
                                BestillingBatchRepository.markAs(tx, batchId, BestillingBatchStatus.Ferdig)
                                logger.info("Oppdateringsbatch $batchId ferdig behandlet")
                            }
                        }

                        ResponseStatus.UGYLDIG_INNTEKTSAAR.name -> {
                            BestillingBatchRepository.markAs(tx, batchId, BestillingBatchStatus.Feilet)
                            logger.error(
                                "Oppdateringsbatch $batchId feilet med UGYLDIG_INNTEKTSAAR. Batchen må opprettes på nytt. " +
                                    "Bestillingene har blitt tatt vare på for å muliggjøre manuell håndtering",
                            )
                        }

                        ResponseStatus.INGEN_ENDRINGER.name -> {
                            // ingenting å se her
                            BestillingBatchRepository.markAs(tx, batchId, BestillingBatchStatus.Ferdig)
                            BestillingRepository.retryUnprocessedBestillings(tx, batchId)
                            logger.info("Ingen oppdaterte skattekort på batch $batchId")
                        }

                        else -> {
                            logger.error { "Oppdateringsbatch $batchId feilet: ${response.status}" }
                            BestillingBatchRepository.markAs(tx, batchId, BestillingBatchStatus.Feilet)
                            AuditRepository.insertBatch(
                                tx,
                                AuditTag.HENTING_AV_SKATTEKORT_FEILET,
                                BestillingRepository.getAllBestillingsInBatch(tx, batchId).map { bestilling -> bestilling.personId },
                                "Batchhenting av skattekort avvist av Skatteetaten med status: ${response.status}",
                            )
                        }
                    }
                } else {
                    logger.info("Oppdateringsbatch $batchId behandles igjen senere når svaret er klart")
                }
            } catch (e: BatchUpdateException) {
                logger.error(marker = TEAM_LOGS_MARKER, e) { "Henting av skattekort for batch $batchId feilet: ${e.message}" }
                logger.error("Henting av skattekort for batch $batchId feilet, detaljer er logget til secure log")
                dataSource.transaction { errorTx ->
                    BestillingBatchRepository.markAs(errorTx, batchId, BestillingBatchStatus.Feilet)
                    AuditRepository.insertBatch(
                        errorTx,
                        AuditTag.HENTING_AV_SKATTEKORT_FEILET,
                        BestillingRepository.getAllBestillingsInBatch(errorTx, batchId).map { bestilling -> bestilling.personId },
                        "Batchhenting av skattekort feilet, batchId $batchId",
                    )
                }
                throw e // For å rulle tilbake "tx"
            } catch (cnpe: CallNotPermittedException) {
                // Her venter vi med å bestille fordi vi tror det er et forbigående problem med kommunikasjon mot skatteetaten
                throw cnpe
            } catch (ex: Exception) {
                logger.error(ex) { "Henting av skattekort for batch $batchId feilet: ${ex.message}" }
                dataSource.transaction { errorTx ->
                    BestillingBatchRepository.markAs(errorTx, batchId, BestillingBatchStatus.Feilet)
                    AuditRepository.insertBatch(
                        errorTx,
                        AuditTag.HENTING_AV_SKATTEKORT_FEILET,
                        BestillingRepository.getAllBestillingsInBatch(errorTx, batchId).map { bestilling -> bestilling.personId },
                        "Batchhenting av skattekort feilet, batchId $batchId",
                    )
                }
                throw ex // For å rulle tilbake "tx"
            }
        }
    }

    companion object {
        val oppdateringerMottattCounter =
            counter(
                name = "oppdaterte_skattekort",
                helpText = "Mottatte oppdateringer av skattekort",
            )
    }
}
