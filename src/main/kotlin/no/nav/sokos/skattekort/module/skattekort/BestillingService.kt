package no.nav.sokos.skattekort.module.skattekort

import java.sql.BatchUpdateException
import javax.sql.DataSource

import kotlin.time.ExperimentalTime
import kotlinx.coroutines.runBlocking

import io.github.resilience4j.circuitbreaker.CallNotPermittedException
import kotliquery.TransactionalSession
import mu.KotlinLogging
import org.postgresql.util.PSQLException

import no.nav.sokos.skattekort.config.TEAM_LOGS_MARKER
import no.nav.sokos.skattekort.infrastructure.Metrics.counter
import no.nav.sokos.skattekort.infrastructure.UnleashIntegration
import no.nav.sokos.skattekort.module.forespoersel.AbonnementRepository
import no.nav.sokos.skattekort.module.person.AuditRepository
import no.nav.sokos.skattekort.module.person.AuditTag
import no.nav.sokos.skattekort.module.person.PersonId
import no.nav.sokos.skattekort.module.person.PersonRepository
import no.nav.sokos.skattekort.module.person.Personidentifikator
import no.nav.sokos.skattekort.module.skattekort.ResultatForSkattekort.IkkeSkattekort
import no.nav.sokos.skattekort.module.skattekort.ResultatForSkattekort.IkkeTrekkplikt
import no.nav.sokos.skattekort.module.skattekort.ResultatForSkattekort.SkattekortopplysningerOK
import no.nav.sokos.skattekort.module.skattekort.ResultatForSkattekort.UgyldigFoedselsEllerDnummer
import no.nav.sokos.skattekort.module.skattekort.ResultatForSkattekort.UgyldigOrganisasjonsnummer
import no.nav.sokos.skattekort.module.skattekort.ResultatForSkattekort.UtgaattDnummerSkattekortForFoedselsnummerErLevert
import no.nav.sokos.skattekort.module.utsending.Utsending
import no.nav.sokos.skattekort.module.utsending.UtsendingRepository
import no.nav.sokos.skattekort.skatteetaten.SkatteetatenClient
import no.nav.sokos.skattekort.skatteetaten.bestillskattekort.bestillOppdateringRequest
import no.nav.sokos.skattekort.skatteetaten.hentskattekort.Arbeidstaker
import no.nav.sokos.skattekort.util.SQLUtils.transaction

private val logger = KotlinLogging.logger {}

class BestillingService(
    private val dataSource: DataSource,
    private val skatteetatenClient: SkatteetatenClient,
    private val featureToggles: UnleashIntegration,
) {
    @OptIn(ExperimentalTime::class)
    fun hentSkattekort() {
        /* denne er med vilje ikke underlagt feature switch-styring for å unngå at en sendt bestilling
        ikke timer ut mens feature-toggelen er slått av
         */

        dataSource
            .transaction { tx ->
                BestillingBatchRepository.getUnprocessedBestillingsBatches(tx)
            }.forEach { bestillingsbatch ->
                dataSource.transaction { tx ->
                    val batchId = bestillingsbatch.id!!.id
                    logger.info("Henter skattekort for ${bestillingsbatch.bestillingsreferanse}")
                    runBlocking {
                        try {
                            val response = skatteetatenClient.hentSkattekort(tx, bestillingsbatch.bestillingsreferanse)
                            if (response != null) {
                                logger.info("Ved henting av skattekort for batch $batchId returnerte Skatteetaten ${response.status}")
                                when (response.status) {
                                    ResponseStatus.FORESPOERSEL_OK.name -> {
                                        response.arbeidsgiver?.first()?.arbeidstaker?.forEach { arbeidstaker ->
                                            handleNyttSkattekort(tx, arbeidstaker, bestillingsbatch.bestillingsreferanse)
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
                                        // her har det skjedd noe alvorlig feil.
                                        BestillingBatchRepository.markAs(tx, batchId, BestillingBatchStatus.Feilet)
                                        logger.error(
                                            "Bestillingsbatch $batchId feilet med UGYLDIG_INNTEKTSAAR. Dette skulle ikke ha skjedd, og batchen må opprettes på nytt. Bestillingene har blitt tatt vare på for å muliggjøre manuell håndtering",
                                        )
                                    }

                                    ResponseStatus.INGEN_ENDRINGER.name -> {
                                        // ingenting å se her
                                        BestillingBatchRepository.markAs(tx, batchId, BestillingBatchStatus.Ferdig)
                                        BestillingRepository.retryUnprocessedBestillings(tx, batchId)
                                        logger.info("Bestillingsbatch $batchId ferdig behandlet uten endringer")
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
                            } else {
                                logger.info("Svaret er ikke klart ennå for bestillingsbatch $batchId, forsøker igjen senere")
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

        val inntektsaar = arbeidstaker.inntektsaar.toInt()

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

    fun hentOppdaterteSkattekort() {
        if (featureToggles.isOppdateringEnabled()) {
            dataSource.transaction { tx ->
                val oppdateringsbatch = BestillingBatchRepository.getUnprocessedOppdateringsBatch(tx)
                if (oppdateringsbatch != null) {
                    haandterOppdateringsbestilling(tx, oppdateringsbatch)
                } else {
                    // Fant ikke noen eksisterende batch, gå og lag ny
                    bestillOppdateringer(tx)
                }
            }
        } else {
            logger.debug("Bestillinger er disablet")
        }
    }

    private fun haandterOppdateringsbestilling(
        tx: TransactionalSession,
        oppdateringsbatch: BestillingBatch,
    ): Any {
        val batchId = oppdateringsbatch.id!!.id
        logger.info("Henter skattekort for ${oppdateringsbatch.bestillingsreferanse}")
        return runBlocking {
            try {
                val response = skatteetatenClient.hentSkattekort(tx, oppdateringsbatch.bestillingsreferanse)
                if (response != null) {
                    logger.info("Ved henting av skattekort for batch $batchId returnerte Skatteetaten ${response.status}")
                    when (response.status) {
                        ResponseStatus.FORESPOERSEL_OK.name -> {
                            val arbeidstakere = response.arbeidsgiver!!.first().arbeidstaker
                            oppdateringerMottattCounter.inc(arbeidstakere.size.toLong())
                            arbeidstakere.forEach { arbeidstaker ->
                                handleNyttSkattekort(tx, arbeidstaker, oppdateringsbatch.bestillingsreferanse)
                            }
                            BestillingBatchRepository.markAs(tx, batchId, BestillingBatchStatus.Ferdig)
                            logger.info("Bestillingsbatch $batchId ferdig behandlet")
                        }

                        ResponseStatus.UGYLDIG_INNTEKTSAAR.name -> {
                            BestillingBatchRepository.markAs(tx, batchId, BestillingBatchStatus.Feilet)
                            logger.error(
                                "Bestillingsbatch $batchId feilet med UGYLDIG_INNTEKTSAAR. Batchen må opprettes på nytt. " +
                                    "Bestillingene har blitt tatt vare på for å muliggjøre manuell håndtering",
                            )
                        }

                        ResponseStatus.INGEN_ENDRINGER.name -> {
                            // ingenting å se her
                            BestillingBatchRepository.markAs(tx, batchId, BestillingBatchStatus.Ferdig)
                            BestillingRepository.retryUnprocessedBestillings(tx, batchId)
                            logger.info("Bestillingsbatch $batchId ferdig behandlet")
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
                } else {
                    logger.info("Bestillingsbatch $batchId behandles igjen senere når svaret er klart")
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

    private fun bestillOppdateringer(tx: TransactionalSession) {
        runBlocking {
            try {
                ReglerForInntektsaar.inntektsaarAaBestille().map(::bestillOppdateringRequest).forEach { request ->
                    val response = skatteetatenClient.bestillSkattekort(request)
                    logger.info("Bestillingsbatch ${response.bestillingsreferanse} mottatt av Skatteetaten")
                    val bestillingsbatchId =
                        BestillingBatchRepository.insertOppdateringsBatch(
                            tx,
                            bestillingsreferanse = response.bestillingsreferanse,
                            request = request,
                        )
                    logger.info("Bestillingsbatch $bestillingsbatchId opprettet")
                }
            } catch (e: BatchUpdateException) {
                logger.error(marker = TEAM_LOGS_MARKER, e) { "Oppretting av bestillingsbatch for henting av oppdaterte skattekort feilet: ${e.message}" }
                logger.error("Oppretting av bestillingsbatch for henting av oppdaterte skattekort feilet, detaljer er logget til secure log")
                throw e
            } catch (cnpe: CallNotPermittedException) {
                // Her venter vi med å bestille fordi vi tror det er et forbigående problem med kommunikasjon mot skatteetaten
                throw cnpe
            } catch (ex: Exception) {
                logger.error(ex) { "Oppretting av bestillingsbatch for henting av oppdaterte skattekort feilet: ${ex.message}" }
                throw ex
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
