package no.nav.sokos.skattekort.skattekortbestilling

import java.sql.BatchUpdateException
import javax.sql.DataSource

import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json

import io.github.resilience4j.circuitbreaker.CallNotPermittedException
import mu.KotlinLogging

import no.nav.sokos.skattekort.api.model.BestillingsbatchDTO
import no.nav.sokos.skattekort.config.PropertiesConfig
import no.nav.sokos.skattekort.config.RECENT_BATCH_GRACE_PERIOD
import no.nav.sokos.skattekort.config.TEAM_LOGS_MARKER
import no.nav.sokos.skattekort.infrastructure.UnleashIntegration
import no.nav.sokos.skattekort.infrastructure.skatteetaten.SkatteetatenClient
import no.nav.sokos.skattekort.infrastructure.skatteetaten.bestillskattekort.bestillOppdateringRequest
import no.nav.sokos.skattekort.infrastructure.skatteetaten.bestillskattekort.bestillSkattekortRequest
import no.nav.sokos.skattekort.person.AuditRepository
import no.nav.sokos.skattekort.person.AuditTag
import no.nav.sokos.skattekort.skattekort.ReglerForInntektsaar
import no.nav.sokos.skattekort.skattekort.ReglerForInntektsaar.maxInntektsaar
import no.nav.sokos.skattekort.skattekorthenting.Bestilling
import no.nav.sokos.skattekort.skattekorthenting.BestillingRepository
import no.nav.sokos.skattekort.util.SQLUtils.transaction

private val logger = KotlinLogging.logger {}

class BestillingsbatchService(
    private val dataSource: DataSource,
    private val skatteetatenClient: SkatteetatenClient,
    private val featureToggles: UnleashIntegration,
) {
    fun bestillSkattekort() {
        if (!featureToggles.isBestillingerEnabled()) return
        val bestillingList: MutableList<Bestilling> = mutableListOf()

        runCatching {
            bestillingList.addAll(dataSource.transaction { tx -> BestillingRepository.getAllBestilling(tx, maxYear = maxInntektsaar()) })
            bestillingList.ifEmpty { return }

            val request =
                bestillSkattekortRequest(
                    inntektsaar = bestillingList.first().inntektsaar,
                    fnr = bestillingList.map { it.fnr }.distinct(),
                    bestillingOrgnr = PropertiesConfig.getApplicationProperties().bestillingOrgnr,
                )
            val response = runBlocking { skatteetatenClient.bestillSkattekort(request) }

            dataSource.transaction { tx ->
                logger.info { "Bestillingsbatch ${response.bestillingsreferanse} mottatt av Skatteetaten" }
                val bestillingsbatchId = BestillingsbatchRepository.insert(tx, response.bestillingsreferanse, Json.encodeToString(request), BestillingsbatchType.BESTILLING)

                logger.info { "Bestillingsbatch $bestillingsbatchId opprettet" }
                AuditRepository.insertBatch(tx, AuditTag.BESTILLING_SENDT, bestillingList.map { it.personId }, "Bestilling sendt")
                BestillingRepository.updateBestillingsWithBatchId(tx, bestillingList.map { it.id!!.id }, bestillingsbatchId)
            }
        }.onFailure { exception ->
            when (exception) {
                is CallNotPermittedException -> {
                    return
                }

                is BatchUpdateException -> {
                    logger.error(marker = TEAM_LOGS_MARKER, exception) { "Oppretting av bestillingsbatch feilet: ${exception.message}" }
                    logger.error("Oppretting av bestillingsbatch feilet, detaljer er logget til TEAM LOGS")
                    dataSource.transaction { errorTx ->
                        AuditRepository.insertBatch(errorTx, AuditTag.BESTILLING_FEILET, bestillingList.map { it.personId }, "Oppretting av bestilling feilet")
                    }
                }

                else -> {
                    logErrorAsInfoIfRecentBatch("Oppretting av bestillingsbatch feilet", exception)
                }
            }
        }
    }

    fun bestillOppdaterteSkattekort() {
        if (!featureToggles.isOppdateringEnabled()) return
        runCatching {
            dataSource.transaction { tx ->
                if (BestillingsbatchRepository.getAllUnprocessedBestillingsbatch(tx, BestillingsbatchType.OPPDATERING).isNotEmpty()) return@transaction

                // Vi henter skattekort for neste år 15. desember for å ha de klar til første utbetaling i januar, og for å kunne ta juleferie uten trøbbel
                ReglerForInntektsaar.inntektsaarAaBestille().map(::bestillOppdateringRequest).forEach { oppdateringsrequest ->
                    val response =
                        runBlocking {
                            skatteetatenClient.bestillSkattekort(oppdateringsrequest)
                        }
                    logger.info("Bestillingsbatch for henting av oppdaterte skattekort ${response.bestillingsreferanse} mottatt av Skatteetaten")
                    val bestillingsbatchId = BestillingsbatchRepository.insert(tx, response.bestillingsreferanse, Json.encodeToString(oppdateringsrequest), BestillingsbatchType.OPPDATERING)
                    logger.info("Bestillingsbatch for henting av oppdaterte skattekort $bestillingsbatchId opprettet")
                }
            }
        }.onFailure { exception ->
            when (exception) {
                is CallNotPermittedException -> {
                    return
                }

                else -> {
                    logErrorAsInfoIfRecentBatch("Oppretting av bestillingsbatch for henting av oppdaterte skattekort feilet", exception)
                }
            }
        }
    }

    fun getBestillingsbatches(
        instantStart: Instant?,
        instantEnd: Instant?,
    ): List<BestillingsbatchDTO> =
        dataSource
            .transaction { tx ->
                if (instantStart == null && instantEnd == null) {
                    BestillingsbatchRepository.getDefaultBatchInsightResults(tx)
                } else {
                    BestillingsbatchRepository.getFilteredBestillingsbatches(tx, instantStart, instantEnd)
                }
            }.map { (batch, dataMottatt) ->
                BestillingsbatchDTO.toDto(batch, batch.dataSendt, dataMottatt)
            }

    fun getIncompleteBestillingsbatchesWithoutJson(): List<BestillingsbatchDTO> {
        val bestillingsbatches: List<Bestillingsbatch> =
            dataSource
                .transaction { tx ->
                    BestillingsbatchRepository.getIncompleteBatches(tx)
                }
        return bestillingsbatches.map { batch ->
            BestillingsbatchDTO.toDto(batch, dataSendt = null, dataMottatt = null)
        }
    }

    private fun logErrorAsInfoIfRecentBatch(
        errorMessage: String,
        exception: Throwable,
    ) {
        val notFerdigBestilingsbatch = dataSource.transaction { tx -> BestillingsbatchRepository.getFirstNotFerdigBestillingsbatch(tx) }

        if (notFerdigBestilingsbatch != null && notFerdigBestilingsbatch.opprettet.plus(RECENT_BATCH_GRACE_PERIOD) > Clock.System.now()) {
            logger.error(marker = TEAM_LOGS_MARKER, exception) { errorMessage }
            logger.info { "$errorMessage. Feilen ignoreres foreløpig da det allerede finnes en vellykket bestillingsbatch fra den siste timen. Forsøker igjen ved neste kjøring." }
            return
        }

        logger.error(marker = TEAM_LOGS_MARKER, exception) { errorMessage }
        logger.error { "$errorMessage, detaljer er logget til TEAM LOGS" }
    }

    fun rerun(bestillingsreferanse: Bestillingsreferanse) =
        dataSource.transaction { tx ->
            BestillingsbatchRepository.rerun(tx, bestillingsreferanse)
        }

    fun getAllBestillings(): List<Bestilling> = dataSource.transaction(BestillingRepository::getEveryBestilling)
}
