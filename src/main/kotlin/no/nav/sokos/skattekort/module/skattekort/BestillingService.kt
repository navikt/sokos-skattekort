package no.nav.sokos.skattekort.module.skattekort

import java.math.BigDecimal
import java.time.LocalDateTime.now
import javax.sql.DataSource

import kotlin.time.ExperimentalTime
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.toKotlinLocalDateTime

import io.prometheus.metrics.core.metrics.Counter
import kotliquery.TransactionalSession
import mu.KotlinLogging

import no.nav.sokos.skattekort.config.PropertiesConfig
import no.nav.sokos.skattekort.infrastructure.METRICS_NAMESPACE
import no.nav.sokos.skattekort.infrastructure.Metrics.prometheusMeterRegistry
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

// TODO: Metrikk: bestillinger per system
// TODO: Metrikk for varsling: tid siden siste mottatte bestilling
// TODO: Metrikk: Eldste bestilling i databasen som ikke er fullført.
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
                    val request =
                        bestillSkattekortRequest(bestillings.firstOrNull()!!.inntektsaar, bestillings.map { it.fnr }, applicationProperties.bestillingOrgnr)

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
        } else {
            logger.debug("Bestillinger er disablet")
        }
    }

    @OptIn(ExperimentalTime::class)
    fun hentSkattekort() {
        /* denne er med vilje ikke underlagt feature switch-styring for å unngå at en sendt bestilling
        ikke timer ut mens feature-toggelen er slått av
         */
        dataSource.transaction { tx ->
            BestillingBatchRepository.getUnprocessedBestillingsBatch(tx)?.let { bestillingsbatch ->
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
                                    }
                                    BestillingBatchRepository.markAs(tx, batchId, BestillingBatchStatus.Ferdig)
                                    BestillingRepository.deleteProcessedBestillings(tx, batchId)
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
                                    BestillingRepository.deleteProcessedBestillings(tx, batchId)
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
                            BestillingRepository.deleteProcessedBestillings(tx, batchId)
                            logger.info("Bestillingsbatch $batchId ferdig behandlet")
                        }
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
        SkattekortRepository.insert(tx, skattekort)
        opprettUtsendingerForAbonnementer(tx, person, inntektsaar)
    }

    @OptIn(ExperimentalTime::class)
    private fun opprettUtsendingerForAbonnementer(
        tx: TransactionalSession,
        person: Person,
        inntektsaar: Int,
    ) {
        AbonnementRepository.finnAktiveAbonnement(tx, person.id!!).forEach { (aboid, system) ->
            UtsendingRepository.insert(
                tx,
                Utsending(
                    abonnementId = aboid,
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
            ResultatForSkattekort.SkattekortopplysningerOK ->
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

            ResultatForSkattekort.IkkeSkattekort -> {
                val forskuddstrekkList = genererForskuddstrekk(arbeidstaker.tilleggsopplysning)
                val kilde = if (forskuddstrekkList.isEmpty()) SkattekortKilde.MANGLER else SkattekortKilde.SYNTETISERT

                Skattekort(
                    personId = person.id!!,
                    utstedtDato = null,
                    identifikator = null,
                    inntektsaar = Integer.parseInt(arbeidstaker.inntektsaar),
                    kilde = kilde.value,
                    resultatForSkattekort = ResultatForSkattekort.IkkeSkattekort,
                    forskuddstrekkList = forskuddstrekkList,
                    tilleggsopplysningList = arbeidstaker.tilleggsopplysning?.map { Tilleggsopplysning.fromValue(it) } ?: emptyList(),
                )
            }

            else ->
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

    private fun genererForskuddstrekk(tilleggsopplysning: List<String>?): List<Forskuddstrekk> {
        if (tilleggsopplysning.isNullOrEmpty()) {
            return emptyList()
        }

        return when {
            tilleggsopplysning.contains("oppholdPaaSvalbard") ->
                listOf<Forskuddstrekk>(
                    Prosentkort(
                        trekkode = Trekkode.LOENN_FRA_NAV,
                        prosentSats = BigDecimal.valueOf(15.70),
                    ),
                    Prosentkort(
                        trekkode = Trekkode.UFOERETRYGD_FRA_NAV,
                        prosentSats = BigDecimal.valueOf(15.70),
                    ),
                    Prosentkort(
                        trekkode = Trekkode.PENSJON_FRA_NAV,
                        prosentSats = BigDecimal.valueOf(13.00),
                    ),
                )

            tilleggsopplysning.contains("kildeskattpensjonist") ->
                listOf<Forskuddstrekk>(
                    Prosentkort(
                        trekkode = Trekkode.PENSJON_FRA_NAV,
                        prosentSats = BigDecimal.valueOf(15.00),
                    ),
                )

            else -> emptyList()
        }
    }

    fun hentOppdaterteSkattekort() {
        if (featureToggles.isBestillingerEnabled()) {
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
            Counter
                .builder()
                .name("${METRICS_NAMESPACE}_oppdaterte_skattekort")
                .help("Mottatte oppdateringer av skattekort")
                .withoutExemplars()
                .register(prometheusMeterRegistry.prometheusRegistry)
    }
}
