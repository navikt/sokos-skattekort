package no.nav.sokos.skattekort.module.skattekort

import java.math.BigDecimal
import javax.sql.DataSource

import kotlin.time.ExperimentalTime
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate

import kotliquery.TransactionalSession
import mu.KotlinLogging

import no.nav.sokos.skattekort.module.forespoersel.AbonnementRepository
import no.nav.sokos.skattekort.module.person.AuditRepository
import no.nav.sokos.skattekort.module.person.AuditTag
import no.nav.sokos.skattekort.module.person.Person
import no.nav.sokos.skattekort.module.person.PersonService
import no.nav.sokos.skattekort.module.person.Personidentifikator
import no.nav.sokos.skattekort.module.skattekortpersonapi.v1.Trekkode
import no.nav.sokos.skattekort.module.utsending.Utsending
import no.nav.sokos.skattekort.module.utsending.UtsendingRepository
import no.nav.sokos.skattekort.skatteetaten.SkatteetatenClient
import no.nav.sokos.skattekort.skatteetaten.bestillskattekort.bestillSkattekortRequest
import no.nav.sokos.skattekort.skatteetaten.hentskattekort.Arbeidstaker
import no.nav.sokos.skattekort.util.SQLUtils.transaction

// TODO: Metrikk: bestillinger per system
// TODO: Metrikk for varsling: tid siden siste mottatte bestilling
// TODO: Metrikk: Eldste bestilling i databasen som ikke er fullført.
class BestillingService(
    val dataSource: DataSource,
    val skatteetatenClient: SkatteetatenClient,
    val personService: PersonService,
) {
    private val logger = KotlinLogging.logger {}

    fun opprettBestillingsbatch() {
        val bestillings: List<Bestilling> =
            dataSource.transaction { tx ->
                val allBestilling = BestillingRepository.getAllBestilling(tx)
                allBestilling
                    .filter { it.bestillingsbatchId == null }
                    .filter { it.inntektsaar == allBestilling.firstOrNull()?.inntektsaar }
                    .take(500)
                    .toList()
            }
        if (bestillings.isEmpty()) {
            logger.info("Ingen bestillinger å sende")
            return
        }
        val request =
            bestillSkattekortRequest(bestillings.firstOrNull()!!.inntektsaar, bestillings.map { it.fnr })

        runBlocking {
            try {
                val response = skatteetatenClient.bestillSkattekort(request)
                logger.info("Bestillingsbatch ${response.bestillingsreferanse} mottatt av Skatteetaten")
                dataSource.transaction { tx ->
                    val bestillingsbatchId =
                        BestillingBatchRepository.insert(
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
                }
            } catch (ex: Exception) {
                dataSource.transaction { tx ->
                    AuditRepository.insertBatch(tx, AuditTag.BESTILLING_FEILET, bestillings.map { it.personId }, "Bestilling feilet")
                }
                logger.error(ex) { "Bestillingsbatch feilet: ${ex.message}" }
            }
        }
    }

    @OptIn(ExperimentalTime::class)
    fun hentSkattekort() {
        dataSource.transaction { tx ->
            BestillingBatchRepository.getUnprocessedBatch(tx)?.let { bestillingsbatch ->
                val batchId = bestillingsbatch.id!!.id
                logger.info("Henter skattekort for ${bestillingsbatch.bestillingsreferanse}")
                runBlocking {
                    try {
                        val response = skatteetatenClient.hentSkattekort(bestillingsbatch.bestillingsreferanse)
                        logger.info("Ved henting av skattekort for batch $batchId returnerte Skatteetaten ${response.status}")
                        when (response.status) {
                            ResponseStatus.FORESPOERSEL_OK.name -> {
                                response.arbeidsgiver!!.first().arbeidstaker.map { arbeidstaker ->
                                    handleNyttSkattekort(tx, arbeidstaker)
                                }
                                BestillingBatchRepository.markAs(tx, batchId, BestillingBatchStatus.Ferdig)
                                BestillingRepository.deleteProcessedBestillings(tx, batchId)
                                logger.info("Bestillingsbatch $batchId ferdig behandlet")
                            }

                            else -> {
                                logger.error { "Bestillingsbatch feilet: ${response.status}" }
                                BestillingBatchRepository.markAs(tx, batchId, BestillingBatchStatus.Feilet)
                            }
                        }
                    } catch (ex: Exception) {
                        logger.error(ex) { "Henting av skattekort for batch $batchId feilet: ${ex.message}" }
                        BestillingBatchRepository.markAs(tx, batchId, BestillingBatchStatus.Feilet)
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
            dataSource.transaction { tx ->
                personService.findPersonByFnr(
                    tx = tx,
                    fnr = Personidentifikator(arbeidstaker.arbeidstakeridentifikator),
                ) ?: error("Person med fnr ${arbeidstaker.arbeidstakeridentifikator} ikke funnet ved behandling av skattekortbestilling")
            }
        val inntektsaar = arbeidstaker.inntektsaar.toInt()
        val skattekort = toSkattekort(arbeidstaker, person)
        if (skattekort.resultatForSkattekort == ResultatForSkattekort.UgyldigFoedselsEllerDnummer) {
            personService.flaggPerson(tx, person.id!!)
        }
        SkattekortRepository.insertBatch(
            tx,
            listOf(
                skattekort,
            ),
        )
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
                    tilleggsopplysningList = arbeidstaker.tilleggsopplysning?.map { Tilleggsopplysning(it) } ?: emptyList(),
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
                    tilleggsopplysningList = arbeidstaker.tilleggsopplysning?.map { Tilleggsopplysning(it) } ?: emptyList(),
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
                    tilleggsopplysningList = arbeidstaker.tilleggsopplysning?.map { Tilleggsopplysning(it) } ?: emptyList(),
                )
        }

    fun genererForskuddstrekk(tilleggsopplysning: List<String>?): List<Forskuddstrekk> {
        if (tilleggsopplysning.isNullOrEmpty()) {
            return emptyList()
        }

        return when {
            tilleggsopplysning.contains("oppholdPaaSvalbard") ->
                listOf<Forskuddstrekk>(
                    Prosentkort(
                        trekkode = Trekkode.LOENN_FRA_NAV.value,
                        prosentSats = BigDecimal.valueOf(15.70),
                    ),
                    Prosentkort(
                        trekkode = Trekkode.UFOERETRYGD_FRA_NAV.value,
                        prosentSats = BigDecimal.valueOf(15.70),
                    ),
                    Prosentkort(
                        trekkode = Trekkode.PENSJON_FRA_NAV.value,
                        prosentSats = BigDecimal.valueOf(13.00),
                    ),
                )

            tilleggsopplysning.contains("kildeskattpensjonist") ->
                listOf<Forskuddstrekk>(
                    Prosentkort(
                        trekkode = Trekkode.PENSJON_FRA_NAV.value,
                        prosentSats = BigDecimal.valueOf(15.00),
                    ),
                )

            else -> emptyList()
        }
    }
}
