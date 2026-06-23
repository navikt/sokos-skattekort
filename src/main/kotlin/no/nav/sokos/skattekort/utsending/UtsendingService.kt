package no.nav.sokos.skattekort.utsending

import javax.sql.DataSource

import io.ktor.server.plugins.di.annotations.Named
import jakarta.jms.Queue
import mu.KotlinLogging

import no.nav.sokos.skattekort.config.MQ_BATCH_SIZE
import no.nav.sokos.skattekort.forespoersel.Forsystem
import no.nav.sokos.skattekort.infrastructure.Metrics.counter
import no.nav.sokos.skattekort.infrastructure.Metrics.gauge
import no.nav.sokos.skattekort.infrastructure.UnleashIntegration
import no.nav.sokos.skattekort.infrastructure.dare.UtsendingDareClientService
import no.nav.sokos.skattekort.person.AuditRepository
import no.nav.sokos.skattekort.person.AuditTag
import no.nav.sokos.skattekort.person.PersonRepository
import no.nav.sokos.skattekort.skattekort.SkattekortRepository
import no.nav.sokos.skattekort.util.SQLUtils.transaction
import no.nav.sokos.skattekort.utsending.mq.JmsProducerService
import no.nav.sokos.skattekort.utsending.oppdragz.SkattekortFixedRecordFormatter

class UtsendingService(
    private val dataSource: DataSource,
    private val jmsProducerService: JmsProducerService,
    @Named(value = "leveransekoeOppdragZSkattekort") private val leveransekoeOppdragZSkattekort: Queue,
    @Named(value = "leveransekoeOppdragZSkattekortStor") private val leveransekoeOppdragZSkattekortStor: Queue,
    private val featureToggles: UnleashIntegration,
    private val utsendingDareClientService: UtsendingDareClientService? = null,
) {
    private val logger = KotlinLogging.logger {}

    fun handleUtsending() {
        if (!featureToggles.isUtsendingEnabled()) return
        runCatching {
            while (true) {
                val utsendingMap = dataSource.transaction { UtsendingRepository.getAllUtsendinger(it, MQ_BATCH_SIZE) }.groupBy { it.forsystem }
                if (utsendingMap.isEmpty()) break

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
                        }
                    }
                }
            }
        }.onFailure { exception ->
            logger.error(exception) {}
        }
    }
//        (jmsConnectionFactory.createConnection() ?: error("Kunne ikke koble til JMS")).use { jmsConnection ->
//            jmsConnection.createSession(JMSContext.AUTO_ACKNOWLEDGE).use { jmsSession ->
//                jmsSession.createProducer(leveransekoeOppdragZSkattekort).use { jmsProducer ->
//                    jmsSession.createProducer(leveransekoeOppdragZSkattekortStor).use { jmsProducerStor ->
//
//                        val utsendinger: List<Utsending> =
//                            try {
//                                dataSource.transaction { tx ->
//                                    UtsendingRepository.getAllUtsendinger(tx)
//                                }
//                            } catch (e: Exception) {
//                                logger.error("Feil under henting av utsendinger", e)
//                                throw e
//                            }
//                        utsendingerIKoe.labelValues("uhaandtert").set(utsendinger.size.toDouble())
//                        utsendingerIKoe.labelValues("feilet").set(utsendinger.filterNot { it.failCount == 0 }.size.toDouble())
//                        utsendinger.forEach { utsending ->
//                            dataSource.transaction { tx ->
//                                when (utsending.forsystem) {
//                                    Forsystem.OPPDRAGSSYSTEMET, Forsystem.OPPDRAGSSYSTEMET_STOR -> {
//                                        try {
//                                            val (producer, queueName) =
//                                                when (utsending.forsystem) {
//                                                    Forsystem.OPPDRAGSSYSTEMET -> jmsProducer to leveransekoeOppdragZSkattekort.queueName
//                                                    else -> jmsProducerStor to leveransekoeOppdragZSkattekortStor.queueName
//                                                }
//                                            sendTilOppdragz(tx, utsending.fnr, utsending.inntektsaar, queueName, jmsSession, producer)
//                                            UtsendingRepository.delete(tx, utsending.id!!)
//                                            utsendingOppdragzCounter.inc()
//                                        } catch (e: BatchUpdateException) {
//                                            logger.error(marker = TEAM_LOGS_MARKER, e) { "Feil under sending til oppdragz: ${e.message}" }
//                                            logger.error("Feil under sending til oppdragz, detaljer er logget til TEAM LOGS")
//                                            dataSource.transaction { errorTx ->
//                                                PersonRepository.findPersonByFnr(errorTx, utsending.fnr)?.let { person ->
//                                                    AuditRepository.insert(errorTx, AuditTag.UTSENDING_FEILET, person.id!!, "Utsending feilet")
//                                                }
//                                                UtsendingRepository.increaseFailCount(errorTx, utsending.id, "SQL-feil, feil er logget til TEAM LOGS")
//                                                feiledeUtsendingerOppdragzCounter.inc()
//                                            }
//                                        } catch (e: Exception) {
//                                            logger.error("Feil under sending til oppdragz", e)
//                                            dataSource.transaction { errorTx ->
//                                                PersonRepository.findPersonByFnr(errorTx, utsending.fnr)?.let { person ->
//                                                    AuditRepository.insert(errorTx, AuditTag.UTSENDING_FEILET, person.id!!, "Utsending feilet")
//                                                }
//                                                UtsendingRepository.increaseFailCount(errorTx, utsending.id, e.message ?: "Ukjent feil")
//                                                feiledeUtsendingerOppdragzCounter.inc()
//                                            }
//                                        }
//                                    }
//
//                                    Forsystem.MANUELL -> {
//                                        UtsendingRepository.delete(tx, utsending.id!!)
//                                    }
//
//                                    Forsystem.DARE_POC -> {
//                                        if (utsendingDareClientService == null) {
//                                            logger.error { "UtsendingDareClientService ikke tilgjengelig i prod" }
//                                            return@transaction
//                                        }
//
//                                        try {
//                                            logger.info { "Sender ut skattekort til Dare-Poc" }
//                                            sendTilDarePoc(tx, utsending.fnr, utsending.inntektsaar)
//                                            UtsendingRepository.delete(tx, utsending.id!!)
//                                        } catch (e: Exception) {
//                                            logger.error("Feil under sending til DARE POC", e)
//                                            dataSource.transaction { errorTx ->
//                                                PersonRepository.findPersonByFnr(errorTx, utsending.fnr)?.let { person ->
//                                                    AuditRepository.insert(errorTx, AuditTag.UTSENDING_FEILET, person.id!!, "Utsending feilet")
//                                                }
//                                                UtsendingRepository.increaseFailCount(errorTx, utsending.id, e.message ?: "Ukjent feil")
//                                            }
//                                        }
//                                    }
//                                }
//                            }
//                        }
//                    }
//                }
//            }
//        }
//        dataSource.transaction { tx ->
//            UtsendingRepository.slettGamleBevis(tx)
//        }
//    }

//    private fun sendTilDarePoc(inntektsaar: Int) {
//        val person = PersonRepository.findPersonByFnr(tx, fnr)
//        val skattekort: Skattekort = SkattekortRepository.findAllByPersonId(tx, listOf(person?.id!!), listOf(inntektsaar), showOnlyLatest = true, adminRole = false).first()
//        runBlocking {
//            utsendingDareClientService?.sendSkattekort(
//                skattekortDTO =
//                    SkattekortDTO(
//                        skattekort,
//                        fnr,
//                    ),
//            )
//            AuditRepository.insert(tx, AuditTag.UTSENDING_OK, person.id, "${Forsystem.DARE_POC.value}: Skattekort sendt til ${Forsystem.DARE_POC.value} OK")
//        }
//    }

    private fun utsendingTilOppdragZ(
        forsystem: Forsystem,
        utsendingList: List<Utsending>,
    ) {
        runCatching {
            dataSource.transaction { tx ->
                val inntektsaar = utsendingList.first().inntektsaar
                val personMap =
                    PersonRepository
                        .findAllByFnr(tx, fnr = utsendingList.map { it.fnr.value }.toTypedArray())
                        .associate { person -> person.id!! to person.foedselsnummer }
                val personIdList = personMap.keys.toList()

                val skattekortList = SkattekortRepository.findAllByPersonId(tx, personIdList, listOf(inntektsaar), showOnlyLatest = true, adminRole = false)
                val payloadList = skattekortList.map { skattekort -> SkattekortFixedRecordFormatter(skattekort, personMap[skattekort.personId]!!.fnr.value).format() }
                val queue =
                    when (forsystem) {
                        Forsystem.OPPDRAGSSYSTEMET -> leveransekoeOppdragZSkattekort
                        else -> leveransekoeOppdragZSkattekortStor
                    }
                jmsProducerService.send(payloadList, queue, utsendingOppdragzCounter)
                AuditRepository.insertBatch(tx, AuditTag.UTSENDING_OK, personIdList, "Oppdragz: Skattekort sendt til $queue")
                UtsendingRepository.deleteBatch(tx, utsendingList.map { it.id!! })

                // TODO: Fjern denne featureToggles
                if (featureToggles.isBevisForSendingEnabled()) {
                }
            }
        }.onFailure { exception ->
            dataSource.transaction { tx ->
                val failMessage = exception.message ?: "Ukjent feil"
                UtsendingRepository.increaseFailCount(tx, failMessage, utsendingList.map { it.id!! })
            }
            logger.error(exception) { "Utsending av skattekort til oppdragZ: ${utsendingList.map { it.id }.joinToString()} feilet." }
        }
    }

    fun getAllUtsendinger(): List<Utsending> =
        dataSource.transaction { tx ->
            UtsendingRepository.getAllUtsendinger(tx)
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
