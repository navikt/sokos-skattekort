package no.nav.sokos.skattekort.utsending

import javax.sql.DataSource

import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlinx.coroutines.runBlocking

import io.ktor.server.plugins.di.annotations.Named
import jakarta.jms.Queue
import mu.KotlinLogging

import no.nav.sokos.skattekort.api.model.v2.SkattekortDTO
import no.nav.sokos.skattekort.config.MQ_BATCH_SIZE
import no.nav.sokos.skattekort.forespoersel.Forsystem
import no.nav.sokos.skattekort.infrastructure.Metrics.counter
import no.nav.sokos.skattekort.infrastructure.Metrics.gauge
import no.nav.sokos.skattekort.infrastructure.UnleashIntegration
import no.nav.sokos.skattekort.infrastructure.dare.UtsendingDareClientService
import no.nav.sokos.skattekort.person.AuditRepository
import no.nav.sokos.skattekort.person.AuditTag
import no.nav.sokos.skattekort.skattekort.SkattekortRepository
import no.nav.sokos.skattekort.util.SQLUtils.transaction
import no.nav.sokos.skattekort.utsending.mq.JmsProducerService
import no.nav.sokos.skattekort.utsending.oppdragz.SkattekortFixedRecordFormatter

private val logger = KotlinLogging.logger {}

@OptIn(ExperimentalAtomicApi::class)
class UtsendingService(
    private val dataSource: DataSource,
    private val jmsProducerService: JmsProducerService,
    @Named(value = "leveransekoeOppdragZSkattekort") private val leveransekoeOppdragZSkattekort: Queue,
    @Named(value = "leveransekoeOppdragZSkattekortStor") private val leveransekoeOppdragZSkattekortStor: Queue,
    private val featureToggles: UnleashIntegration,
    private val utsendingDareClientService: UtsendingDareClientService? = null,
) {
    private val totalIUtsending = AtomicInt(0)

    fun handleUtsending() {
        totalIUtsending.store(0)
        if (!featureToggles.isUtsendingEnabled()) return

        runCatching {
            while (true) {
                val utsendingMap =
                    dataSource
                        .transaction { UtsendingRepository.getAllUtsendinger(it, MQ_BATCH_SIZE) }
                        .groupBy { it.forsystem }
                        .filterValues { it.isNotEmpty() }
                if (utsendingMap.isEmpty()) break

                if (totalIUtsending.load() == 0) {
                    logger.info { "Starter utsending-batch" }
                    totalIUtsending.store(0)
                }

                utsendingerIKoe.labelValues("uhaandtert").set(utsendingMap.values.sumOf { it.size }.toDouble())
                utsendingerIKoe.labelValues("feilet").set(
                    utsendingMap.values
                        .flatten()
                        .count { it.failCount != 0 }
                        .toDouble(),
                )

                utsendingMap.forEach { (forsystem, utsendingList) ->
                    when (forsystem) {
                        Forsystem.OPPDRAGSSYSTEMET, Forsystem.OPPDRAGSSYSTEMET_STOR -> utsendingTilOppdragZ(forsystem, utsendingList)
                        Forsystem.MANUELL -> dataSource.transaction { tx -> UtsendingRepository.deleteBatch(tx, utsendingList.map { it.id!! }) }
                        Forsystem.DARE_POC -> {
                            if (utsendingDareClientService == null) {
                                logger.error { "UtsendingDareClientService ikke tilgjengelig i prod" }
                                return@forEach
                            }
                            utsendingTilDarePoc(utsendingList)
                        }
                    }
                    logger.info { "Ferdig med utsending til ${forsystem.value}. Antall behandlet: ${utsendingList.size}" }
                    totalIUtsending.addAndFetch(utsendingList.size)
                }
            }
            if (totalIUtsending.load() > 0) {
                logger.info { "Ferdig utsending-batch. Antall behandlet i batch: ${totalIUtsending.load()}" }
            }
        }.onFailure { exception ->
            logger.error(exception) { "Feil ved henting data under utsending" }
        }
    }

    private fun utsendingTilDarePoc(utsendingList: List<Utsending>) {
        runCatching {
            runBlocking {
                val skattekortList = dataSource.transaction { tx -> SkattekortRepository.getAllById(tx, *utsendingList.map { it.skattekortId.value }.toLongArray()) }
                skattekortList.forEach { skattekort ->
                    val utsending = utsendingList.find { it.skattekortId == skattekort.id }
                    if (utsending != null) {
                        utsendingDareClientService?.sendSkattekort(
                            skattekortDTO = SkattekortDTO(skattekort, utsending.fnr),
                        )
                        dataSource.transaction { tx ->
                            AuditRepository.insert(tx, AuditTag.UTSENDING_OK, skattekort.personId, "${Forsystem.DARE_POC.value}: Skattekort sendt til ${Forsystem.DARE_POC.value} OK")
                            UtsendingRepository.deleteBatch(tx, listOf(utsending.id!!))
                        }
                    } else {
                        logger.error { "Skattekort er sendt til ${Forsystem.DARE_POC}, men fant ingen utsending" }
                    }
                }
            }
        }.onFailure { exception ->
            handleException(exception, Forsystem.DARE_POC, utsendingList)
        }
    }

    private fun utsendingTilOppdragZ(
        forsystem: Forsystem,
        utsendingList: List<Utsending>,
    ) {
        runCatching {
            dataSource.transaction { tx ->
                // Build a deterministic skattekortId→fnr mapping directly from the utsendingList.
                // Each Utsending already carries the exact FNR that was stored when the request was
                // created, so we never need to re-resolve via PersonRepository (which can return
                // multiple rows when a person has more than one identifier).
                val skattekortIdToFnrMap = utsendingList.associate { it.skattekortId to it.fnr }
                val skattekortList = SkattekortRepository.getAllById(tx, *utsendingList.map { it.skattekortId.value }.toLongArray())

                // Pair each skattekort with its payload, skipping those that produce an empty
                // string (no valid forskuddstrekk for NAV). Empty payloads must never be sent to MQ
                // and must not be audited as successful sendings.
                val skattekortOgPayload =
                    skattekortList.mapNotNull { skattekort ->
                        val fnr =
                            skattekortIdToFnrMap[skattekort.id]
                                ?: run {
                                    logger.warn { "Ingen utsending funnet for skattekortId=${skattekort.id} – hoppes over" }
                                    return@mapNotNull null
                                }
                        val payload = SkattekortFixedRecordFormatter(skattekort, fnr.value).format()
                        if (payload.isEmpty()) {
                            logger.info { "Skattekort ${skattekort.id} for personId=${skattekort.personId} produserte tom payload (ingen gyldige forskuddstrekk) – hoppes over" }
                            AuditRepository.insert(tx, AuditTag.UTSENDING_OK, skattekort.personId, "Oppdragz: Skattekort ikke sendt fordi skattekort-formatet ikke kan uttrykke innholdet")
                            null
                        } else {
                            skattekort to payload
                        }
                    }

                val queue =
                    when (forsystem) {
                        Forsystem.OPPDRAGSSYSTEMET -> leveransekoeOppdragZSkattekort
                        else -> leveransekoeOppdragZSkattekortStor
                    }

                if (skattekortOgPayload.isNotEmpty()) {
                    jmsProducerService.send(skattekortOgPayload.map { it.second }, queue, utsendingOppdragzCounter)
                    // Audit only the persons whose skattekort were actually sent to MQ.
                    AuditRepository.insertBatch(
                        tx,
                        AuditTag.UTSENDING_OK,
                        skattekortOgPayload.map { it.first.personId },
                        "Oppdragz: Skattekort sendt til ${queue.queueName}",
                    )
                }

                // Delete all utsendinger in this batch, including those that produced an empty
                // payload – they will never produce content and must not be retried endlessly.
                UtsendingRepository.deleteBatch(tx, utsendingList.map { it.id!! })

                // TODO: Fjern denne featureToggles
                if (featureToggles.isBevisForSendingEnabled()) {
                }
            }
        }.onFailure { exception ->
            handleException(exception, forsystem, utsendingList)
        }
    }

    fun getAllUtsendinger(): List<Utsending> = dataSource.transaction { tx -> UtsendingRepository.getAllUtsendinger(tx, failCount = 0) }

    private fun handleException(
        exception: Throwable,
        forsystem: Forsystem,
        utsendingList: List<Utsending>,
    ) {
        dataSource.transaction { tx ->
            val failMessage = exception.message ?: "Ukjent feil"
            UtsendingRepository.increaseFailCount(tx, failMessage, utsendingList.map { it.id!! })
        }
        val failCountEtterInkrement = utsendingList.maxOf { it.failCount + 1 }
        if (failCountEtterInkrement >= 2) {
            if (forsystem == Forsystem.OPPDRAGSSYSTEMET || forsystem == Forsystem.OPPDRAGSSYSTEMET_STOR) {
                feiledeUtsendingerOppdragzCounter.inc(utsendingList.size.toLong())
            }
            logger.error(exception) { "Utsending av skattekort til ${forsystem.value}: ${utsendingList.map { it.id }.joinToString()} feilet." }
        }
    }

    companion object {
        val utsendingOppdragzCounter =
            counter(
                name = "utsendinger_oppdragz_total",
                helpText = "Utsendinger til oppdrag z",
            )

        val feiledeUtsendingerOppdragzCounter =
            counter(
                name = "utsendinger_oppdragz_feil_total",
                helpText = "Feilede forsøk på utsendinger til oppdrag z",
            )

        val utsendingerIKoe =
            gauge(
                name = "utsendinger_i_koe",
                helpText = "Utsendinger i kø, enda ikke håndtert",
                labelNames = "status",
            )
    }
}
