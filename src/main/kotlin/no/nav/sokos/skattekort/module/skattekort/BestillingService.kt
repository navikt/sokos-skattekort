package no.nav.sokos.skattekort.module.skattekort

import java.time.LocalDateTime.now
import javax.sql.DataSource

import kotlin.time.ExperimentalTime
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.toKotlinLocalDateTime

import kotliquery.TransactionalSession
import mu.KotlinLogging

import no.nav.sokos.skattekort.config.PropertiesConfig
import no.nav.sokos.skattekort.infrastructure.Metrics.counter
import no.nav.sokos.skattekort.infrastructure.UnleashIntegration
import no.nav.sokos.skattekort.module.forespoersel.AbonnementRepository
import no.nav.sokos.skattekort.module.person.AuditRepository
import no.nav.sokos.skattekort.module.person.AuditTag
import no.nav.sokos.skattekort.module.person.Person
import no.nav.sokos.skattekort.module.person.PersonRepository
import no.nav.sokos.skattekort.module.person.Personidentifikator
import no.nav.sokos.skattekort.module.utsending.Utsending
import no.nav.sokos.skattekort.module.utsending.UtsendingRepository
import no.nav.sokos.skattekort.skatteetaten.SkatteetatenClient
import no.nav.sokos.skattekort.skatteetaten.bestillskattekort.BestillSkattekortRequest
import no.nav.sokos.skattekort.skatteetaten.bestillskattekort.bestillOppdateringRequest
import no.nav.sokos.skattekort.skatteetaten.bestillskattekort.bestillSkattekortRequest
import no.nav.sokos.skattekort.skatteetaten.hentskattekort.Arbeidstaker
import no.nav.sokos.skattekort.util.SQLUtils.transaction

class BestillingService(
    private val dataSource: DataSource,
    private val skatteetatenClient: SkatteetatenClient,
    private val featureToggles: UnleashIntegration,
    private val applicationProperties: PropertiesConfig.ApplicationProperties,
) {
    private val logger = KotlinLogging.logger {}

    fun opprettBestillingsbatch() {
        if (featureToggles.isBestillingerEnabled()) {
            dataSource.transaction { tx ->
                if (BestillingBatchRepository.getUnprocessedBestillingsBatches(tx).size > 10) {
                    logger.warn("Oppretter ikke ny bestillingsbatch fordi for mange ubehandlede allerede ligger i kø")
                } else {
                    val now = now().toKotlinLocalDateTime()
                    val bestillings: List<Bestilling> =
                        BestillingRepository.getBestillingsKandidaterForBatch(
                            tx,
                            maxYear =
                                if (now.day >= 15 && now.month == Month.DECEMBER) {
                                    now.year + 1
                                } else {
                                    now.year
                                },
                        )
                    if (bestillings.isEmpty()) {
                        logger.info("Ingen bestillinger å sende")
                    } else {
                        val request = bestillSkattekortRequest(bestillings.firstOrNull()!!.inntektsaar, bestillings.map { it.fnr }, applicationProperties.bestillingOrgnr)

                        runBlocking {
                            try {
                                val response = skatteetatenClient.bestillSkattekort(request)
                                logger.info("Bestillingsbatch ${response.bestillingsreferanse} mottatt av Skatteetaten")
                                val bestillingsbatchId =
                                    BestillingBatchRepository.insertBestillingsBatch(
                                        tx,
                                        bestillingsreferanse = response.bestillingsreferanse,
                                        request = request,
                                    )
                                logger.info("Bestillingsbatch $bestillingsbatchId opprettet")
                                AuditRepository.insertBatch(tx, AuditTag.BESTILLING_SENDT, bestillings.map { it.personId }, "Bestilling sendt")
                                BestillingRepository.updateBestillingsWithBatchId(
                                    tx,
                                    bestillings.map { it.id!!.id },
                                    bestillingsbatchId,
                                )
                            } catch (ex: Exception) {
                                dataSource.transaction { errorTx ->
                                    AuditRepository.insertBatch(errorTx, AuditTag.BESTILLING_FEILET, bestillings.map { it.personId }, "Bestilling feilet")
                                }
                                logger.error(ex) { "Bestillingsbatch feilet: ${ex.message}" }
                                throw ex
                            }
                        }
                    }
                }
            }
        } else {
            logger.debug("Bestillinger er disablet")
        }
    }

    @OptIn(ExperimentalTime::class)
    fun hentSkattekort() {
        /* denne er med vilje ikke underlagt feature switch-styring for å unngå at en sendt bestilling
        ikke timer ut mens feature-toggelen er slått av
         */
        val batcher =
            dataSource.transaction { tx ->
                BestillingBatchRepository.getUnprocessedBestillingsBatches(tx)
            }
        batcher.forEach { bestillingsbatch ->
            dataSource.transaction { tx ->
                val batchId = bestillingsbatch.id!!.id
                logger.info("Henter skattekort for ${bestillingsbatch.bestillingsreferanse}")
                runBlocking {
                    try {
                        val response = skatteetatenClient.hentSkattekort(bestillingsbatch.bestillingsreferanse)
                        if (response != null) {
                            logger.info("Ved henting av skattekort for batch $batchId returnerte Skatteetaten ${response.status}")
                            when (response.status) {
                                ResponseStatus.FORESPOERSEL_OK.name -> {
                                    response.arbeidsgiver!!.first().arbeidstaker.forEach { arbeidstaker ->
                                        handleNyttSkattekort(tx, arbeidstaker)
                                        BestillingRepository.deleteProcessedBestilling(tx, batchId, arbeidstaker.arbeidstakeridentifikator)
                                        // TODO Kanskje sjekke om det er samme fnr som vi har bestilt for(fnr-endring på personen?)
                                    }
                                    BestillingBatchRepository.markAs(tx, batchId, BestillingBatchStatus.Ferdig)
                                    BestillingRepository.retryUnprocessedBestillings(tx, batchId)
                                    logger.info("Bestillingsbatch $batchId ferdig behandlet")
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
                            // Ingen skattekort returnert
                            BestillingBatchRepository.markAs(tx, batchId, BestillingBatchStatus.Ferdig)
                            BestillingRepository.retryUnprocessedBestillings(tx, batchId)
                            logger.info("Bestillingsbatch $batchId ferdig behandlet")
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
                    } catch (ex: Exception) {
                        dataSource.transaction { errorTx ->
                            logger.error(ex) { "Henting av skattekort for batch $batchId feilet: ${ex.message}" }
                            BestillingBatchRepository.markAs(errorTx, batchId, BestillingBatchStatus.Feilet)
                            AuditRepository.insertBatch(
                                errorTx,
                                AuditTag.HENTING_AV_SKATTEKORT_FEILET,
                                BestillingRepository.getAllBestillingsInBatch(tx, batchId).map { bestilling -> bestilling.personId },
                                "Batchhenting av skattekort feilet",
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
    ) {
        val person =
            PersonRepository.findPersonByFnr(
                tx = tx,
                fnr = Personidentifikator(arbeidstaker.arbeidstakeridentifikator),
            ) ?: error("Person med fnr ${arbeidstaker.arbeidstakeridentifikator} ikke funnet ved behandling av skattekortbestilling")
        val inntektsaar = arbeidstaker.inntektsaar.toInt()
        val skattekort = toSkattekort(arbeidstaker, person)
        if (skattekort.resultatForSkattekort == ResultatForSkattekort.UgyldigFoedselsEllerDnummer) {
            PersonRepository.flaggPerson(tx, person.id!!)
        }
        val id = SkattekortId(SkattekortRepository.insert(tx, skattekort))

        Syntetisering.evtSyntetiserSkattekort(skattekort, id)?.let { (syntetisertSkattekort, aarsak) ->
            SkattekortRepository.insert(tx, syntetisertSkattekort)
            AuditRepository.insert(tx, AuditTag.SYNTETISERT_SKATTEKORT, person.id!!, aarsak)
        }

        opprettUtsendingerForAbonnementer(tx, person, inntektsaar)
    }

    @OptIn(ExperimentalTime::class)
    private fun opprettUtsendingerForAbonnementer(
        tx: TransactionalSession,
        person: Person,
        inntektsaar: Int,
    ) {
        AbonnementRepository.finnAktiveSystemer(tx, person.id!!, inntektsaar).forEach { system ->
            UtsendingRepository.insert(
                tx,
                Utsending(
                    inntektsaar = inntektsaar,
                    fnr = person.foedselsnummer.fnr,
                    forsystem = system,
                ),
            )
        }
    }

    @OptIn(ExperimentalTime::class)
    private fun toSkattekort(
        arbeidstaker: Arbeidstaker,
        person: Person,
    ): Skattekort =
        when (ResultatForSkattekort.fromValue(arbeidstaker.resultatForSkattekort)) {
            ResultatForSkattekort.SkattekortopplysningerOK -> {
                Skattekort(
                    personId = person.id!!,
                    utstedtDato = LocalDate.parse(arbeidstaker.skattekort!!.utstedtDato),
                    identifikator = arbeidstaker.skattekort.skattekortidentifikator.toString(),
                    inntektsaar = Integer.parseInt(arbeidstaker.inntektsaar),
                    kilde = SkattekortKilde.SKATTEETATEN.value,
                    resultatForSkattekort = ResultatForSkattekort.SkattekortopplysningerOK,
                    forskuddstrekkList = arbeidstaker.skattekort.forskuddstrekk.map { Forskuddstrekk.create(it) },
                    tilleggsopplysningList = arbeidstaker.tilleggsopplysning?.map { Tilleggsopplysning.fromValue(it) } ?: emptyList(),
                )
            }

            ResultatForSkattekort.IkkeSkattekort -> {
                Skattekort(
                    personId = person.id!!,
                    utstedtDato = null,
                    identifikator = null,
                    inntektsaar = Integer.parseInt(arbeidstaker.inntektsaar),
                    kilde = SkattekortKilde.SKATTEETATEN.value,
                    resultatForSkattekort = ResultatForSkattekort.IkkeSkattekort,
                    forskuddstrekkList = emptyList(),
                    tilleggsopplysningList = arbeidstaker.tilleggsopplysning?.map { Tilleggsopplysning.fromValue(it) } ?: emptyList(),
                )
            }

            ResultatForSkattekort.IkkeTrekkplikt -> {
                Skattekort(
                    personId = person.id!!,
                    utstedtDato = null,
                    identifikator = null,
                    inntektsaar = Integer.parseInt(arbeidstaker.inntektsaar),
                    kilde = SkattekortKilde.SKATTEETATEN.value,
                    resultatForSkattekort = ResultatForSkattekort.IkkeTrekkplikt,
                    forskuddstrekkList = emptyList(),
                    tilleggsopplysningList = arbeidstaker.tilleggsopplysning?.map { Tilleggsopplysning.fromValue(it) } ?: emptyList(),
                )
            }

            ResultatForSkattekort.UgyldigOrganisasjonsnummer -> {
                throw UgyldigOrganisasjonsnummerException("Ugyldig organisasjonsnummer")
            }

            else -> {
                Skattekort(
                    personId = person.id!!,
                    utstedtDato = null,
                    identifikator = null,
                    inntektsaar = Integer.parseInt(arbeidstaker.inntektsaar),
                    kilde = SkattekortKilde.SKATTEETATEN.value,
                    resultatForSkattekort = ResultatForSkattekort.fromValue(arbeidstaker.resultatForSkattekort),
                    tilleggsopplysningList = arbeidstaker.tilleggsopplysning?.map { Tilleggsopplysning.fromValue(it) } ?: emptyList(),
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
                val response = skatteetatenClient.hentSkattekort(oppdateringsbatch.bestillingsreferanse)
                if (response != null) {
                    logger.info("Ved henting av skattekort for batch $batchId returnerte Skatteetaten ${response.status}")
                    when (response.status) {
                        ResponseStatus.FORESPOERSEL_OK.name -> {
                            val arbeidstakere = response.arbeidsgiver!!.first().arbeidstaker
                            oppdateringerMottattCounter.inc(arbeidstakere.size.toLong())
                            arbeidstakere.forEach { arbeidstaker ->
                                handleNyttSkattekort(tx, arbeidstaker)
                            }
                            BestillingBatchRepository.markAs(tx, batchId, BestillingBatchStatus.Ferdig)
                            logger.info("Bestillingsbatch $batchId ferdig behandlet")
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
                    // Ingen oppdateringer
                    BestillingBatchRepository.markAs(tx, batchId, BestillingBatchStatus.Ferdig)
                    logger.info("Bestillingsbatch $batchId ferdig behandlet")
                }
            } catch (ex: Exception) {
                logger.error(ex) { "Henting av skattekort for batch $batchId feilet: ${ex.message}" }
                dataSource.transaction { errorTx ->
                    BestillingBatchRepository.markAs(errorTx, batchId, BestillingBatchStatus.Feilet)
                    AuditRepository.insertBatch(
                        errorTx,
                        AuditTag.HENTING_AV_SKATTEKORT_FEILET,
                        BestillingRepository.getAllBestillingsInBatch(errorTx, batchId).map { bestilling -> bestilling.personId },
                        "Batchhenting av skattekort feilet",
                    )
                }
                throw ex // For å rulle tilbake "tx"
            }
        }
    }

    private fun bestillOppdateringer(tx: TransactionalSession) {
        val now = now().toKotlinLocalDateTime()
        val erEtterMidtenAvDesember = (now.day > 15 && now.month == Month.DECEMBER)
        val requests: List<BestillSkattekortRequest> =
            if (erEtterMidtenAvDesember) {
                listOf(now.year, now.year + 1)
            } else {
                listOf(now.year)
            }.map { aar ->
                bestillOppdateringRequest(aar)
            }

        runBlocking {
            try {
                requests.forEach { request ->
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
