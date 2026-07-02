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
import no.nav.sokos.skattekort.person.Foedselsnummer
import no.nav.sokos.skattekort.person.PersonId
import no.nav.sokos.skattekort.person.PersonRepository
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
                    val personIdMap =
                        dataSource.transaction { tx ->
                            PersonRepository
                                .findAllByFnr(tx, *utsendingList.map { it.fnr.value }.toTypedArray())
                                .associate { person -> person.id!! to person.foedselsnummer }
                        }

                    when (forsystem) {
                        Forsystem.OPPDRAGSSYSTEMET, Forsystem.OPPDRAGSSYSTEMET_STOR -> utsendingTilOppdragZ(forsystem, personIdMap, utsendingList)
                        Forsystem.MANUELL -> dataSource.transaction { tx -> UtsendingRepository.deleteBatch(tx, utsendingList.map { it.id!! }) }
                        Forsystem.DARE_POC -> {
                            if (utsendingDareClientService == null) {
                                logger.error { "UtsendingDareClientService ikke tilgjengelig i prod" }
                                return@forEach
                            }
                            utsendingTilDarePoc(personIdMap, utsendingList)
                        }
                    }
                    logger.info { "Ferdig med utsending til ${forsystem.value}. Antall behandlet: ${utsendingList.size}" }
                }
            }
            logger.info { "Ferdig utsending-batch. Antall behandlet i batch: ${totalIUtsending.load()}" }
        }.onFailure { exception ->
            logger.error(exception) { "Feil av henting data under utsending" }
        }
    }

    private fun utsendingTilDarePoc(
        personIdMap: Map<PersonId, Foedselsnummer>,
        utsendingList: List<Utsending>,
    ) {
        runCatching {
            runBlocking {
                val skattekortList =
                    dataSource.transaction { tx ->
                        SkattekortRepository.getAllById(tx, *utsendingList.map { it.skattekortId.value }.toLongArray())
                    }
                skattekortList.forEach { skattekort ->
                    val personidentifikator = personIdMap[skattekort.personId]!!.fnr

                    utsendingDareClientService?.sendSkattekort(
                        skattekortDTO =
                            SkattekortDTO(
                                skattekort,
                                personidentifikator,
                            ),
                    )
                    dataSource.transaction { tx ->
                        AuditRepository.insert(tx, AuditTag.UTSENDING_OK, skattekort.personId, "${Forsystem.DARE_POC.value}: Skattekort sendt til ${Forsystem.DARE_POC.value} OK")
                        val utsendingId = utsendingList.find { it.fnr == personidentifikator && it.forsystem == Forsystem.DARE_POC }!!.id!!
                        UtsendingRepository.deleteBatch(tx, listOf(utsendingId))
                    }
                }
            }
        }.onFailure { exception ->
            handleException(exception, Forsystem.DARE_POC, utsendingList)
        }
    }

    private fun utsendingTilOppdragZ(
        forsystem: Forsystem,
        personIdMap: Map<PersonId, Foedselsnummer>,
        utsendingList: List<Utsending>,
    ) {
        runCatching {
            dataSource.transaction { tx ->
                val personIdList = personIdMap.keys.toList()
                val skattekortList = SkattekortRepository.getAllById(tx, *utsendingList.map { it.skattekortId.value }.toLongArray())
                val payloadList = skattekortList.map { skattekort -> SkattekortFixedRecordFormatter(skattekort, personIdMap[skattekort.personId]!!.fnr.value).format() }
                val queue =
                    when (forsystem) {
                        Forsystem.OPPDRAGSSYSTEMET -> leveransekoeOppdragZSkattekort
                        else -> leveransekoeOppdragZSkattekortStor
                    }
                jmsProducerService.send(payloadList, queue, utsendingOppdragzCounter)
                AuditRepository.insertBatch(tx, AuditTag.UTSENDING_OK, personIdList, "Oppdragz: Skattekort sendt til ${queue.queueName}")
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
        if (utsendingList.first().failCount >= 2) {
            feiledeUtsendingerOppdragzCounter.inc(utsendingList.size.toLong())
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
