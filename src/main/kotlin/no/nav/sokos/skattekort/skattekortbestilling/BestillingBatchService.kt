package no.nav.sokos.skattekort.skattekortbestilling

import java.sql.BatchUpdateException
import javax.sql.DataSource

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json

import io.github.resilience4j.circuitbreaker.CallNotPermittedException
import kotliquery.TransactionalSession
import mu.KotlinLogging

import no.nav.sokos.skattekort.config.PropertiesConfig
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

class BestillingBatchService(
    private val dataSource: DataSource,
    private val skatteetatenClient: SkatteetatenClient,
    private val featureToggles: UnleashIntegration,
) {
    fun opprettBestillingsbatch() {
        featureToggles.takeIf { it.isBestillingerEnabled() } ?: return
        val bestillingList: MutableList<Bestilling> = mutableListOf()

        runCatching {
            bestillingList.addAll(dataSource.transaction { tx -> BestillingRepository.getBestillingsKandidaterForBatch(tx, maxYear = maxInntektsaar()) })
            bestillingList.ifEmpty { return }

            val (request, response) =
                runBlocking {
                    val request =
                        bestillSkattekortRequest(
                            inntektsaar = bestillingList.firstOrNull()!!.inntektsaar,
                            fnr = bestillingList.map { it.fnr }.distinct(),
                            bestillingOrgnr = PropertiesConfig.getApplicationProperties().bestillingOrgnr,
                        )
                    Pair(request, skatteetatenClient.bestillSkattekort(request))
                }

            dataSource.transaction { tx ->
                logger.info { "Bestillingsbatch ${response.bestillingsreferanse} mottatt av Skatteetaten" }
                val bestillingsbatchId = BestillingBatchRepository.insertBestillingsBatch(tx, bestillingsreferanse = response.bestillingsreferanse, dataSendt = Json.encodeToString(request))

                logger.info { "Bestillingsbatch $bestillingsbatchId opprettet" }
                AuditRepository.insertBatch(tx, AuditTag.BESTILLING_SENDT, bestillingList.map { it.personId }, "Bestilling sendt")
                BestillingRepository.updateBestillingsWithBatchId(tx, bestillingList.map { it.id!!.id }, bestillingsbatchId)
            }
        }.onFailure { exception ->
            when (exception) {
                is CallNotPermittedException -> return
                is BatchUpdateException -> {
                    logger.error(marker = TEAM_LOGS_MARKER, exception) { "Oppretting av bestillingsbatch feilet: ${exception.message}" }
                    logger.error("Oppretting av bestillingsbatch feilet, detaljer er logget til secure log")
                    auditLogBestillingFeilet(bestillingList)
                }

                else -> {
                    auditLogBestillingFeilet(bestillingList)
                    logger.error(exception) { "Oppretting av bestillingsbatch feilet: ${exception.message}" }
                }
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

    private fun auditLogBestillingFeilet(bestillingList: List<Bestilling>) {
        dataSource.transaction { errorTx ->
            AuditRepository.insertBatch(errorTx, AuditTag.BESTILLING_FEILET, bestillingList.map { it.personId }, "Oppretting av bestilling feilet")
        }
    }
}
