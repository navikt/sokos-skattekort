package no.nav.sokos.skattekort.utsending

import java.sql.BatchUpdateException
import javax.sql.DataSource

import kotlinx.coroutines.runBlocking

import io.ktor.server.plugins.di.annotations.Named
import jakarta.jms.ConnectionFactory
import jakarta.jms.JMSContext
import jakarta.jms.MessageProducer
import jakarta.jms.Queue
import jakarta.jms.Session
import kotliquery.TransactionalSession
import mu.KotlinLogging

import no.nav.sokos.skattekort.api.model.v2.SkattekortDTO
import no.nav.sokos.skattekort.config.TEAM_LOGS_MARKER
import no.nav.sokos.skattekort.forespoersel.Forsystem
import no.nav.sokos.skattekort.infrastructure.Metrics.counter
import no.nav.sokos.skattekort.infrastructure.Metrics.gauge
import no.nav.sokos.skattekort.infrastructure.UnleashIntegration
import no.nav.sokos.skattekort.infrastructure.dare.UtsendingDareClientService
import no.nav.sokos.skattekort.person.AuditRepository
import no.nav.sokos.skattekort.person.AuditTag
import no.nav.sokos.skattekort.person.PersonId
import no.nav.sokos.skattekort.person.PersonRepository
import no.nav.sokos.skattekort.person.Personidentifikator
import no.nav.sokos.skattekort.skattekort.Skattekort
import no.nav.sokos.skattekort.skattekort.SkattekortRepository
import no.nav.sokos.skattekort.util.SQLUtils.transaction
import no.nav.sokos.skattekort.utsending.oppdragz.SkattekortFixedRecordFormatter

class UtsendingService(
    private val dataSource: DataSource,
    private val jmsConnectionFactory: ConnectionFactory,
    @Named(value = "leveransekoeOppdragZSkattekort") private val leveransekoeOppdragZSkattekort: Queue,
    @Named(value = "leveransekoeOppdragZSkattekortStor") private val leveransekoeOppdragZSkattekortStor: Queue,
    private val featureToggles: UnleashIntegration,
    private val utsendingDareClientService: UtsendingDareClientService? = null,
) {
    private val logger = KotlinLogging.logger {}

    fun handleUtsending() {
        if (!featureToggles.isUtsendingEnabled()) return
        (jmsConnectionFactory.createConnection() ?: error("Kunne ikke koble til JMS")).use { jmsConnection ->
            jmsConnection.createSession(JMSContext.AUTO_ACKNOWLEDGE).use { jmsSession ->
                jmsSession.createProducer(leveransekoeOppdragZSkattekort).use { jmsProducer ->
                    jmsSession.createProducer(leveransekoeOppdragZSkattekortStor).use { jmsProducerStor ->

                        val utsendinger: List<Utsending> =
                            try {
                                dataSource.transaction { tx ->
                                    UtsendingRepository.getAllUtsendinger(tx)
                                }
                            } catch (e: Exception) {
                                logger.error("Feil under henting av utsendinger", e)
                                throw e
                            }
                        utsendingerIKoe.labelValues("uhaandtert").set(utsendinger.size.toDouble())
                        utsendingerIKoe.labelValues("feilet").set(utsendinger.filterNot { it.failCount == 0 }.size.toDouble())
                        utsendinger.forEach { utsending ->
                            dataSource.transaction { tx ->
                                when (utsending.forsystem) {
                                    Forsystem.OPPDRAGSSYSTEMET, Forsystem.OPPDRAGSSYSTEMET_STOR -> {
                                        try {
                                            val (producer, queueName) =
                                                when (utsending.forsystem) {
                                                    Forsystem.OPPDRAGSSYSTEMET -> jmsProducer to leveransekoeOppdragZSkattekort.queueName
                                                    else -> jmsProducerStor to leveransekoeOppdragZSkattekortStor.queueName
                                                }
                                            sendTilOppdragz(tx, utsending.fnr, utsending.inntektsaar, queueName, jmsSession, producer)
                                            UtsendingRepository.delete(tx, utsending.id!!)
                                            utsendingOppdragzCounter.inc()
                                        } catch (e: BatchUpdateException) {
                                            logger.error(marker = TEAM_LOGS_MARKER, e) { "Feil under sending til oppdragz: ${e.message}" }
                                            logger.error("Feil under sending til oppdragz, detaljer er logget til TEAM LOGS")
                                            dataSource.transaction { errorTx ->
                                                PersonRepository.findPersonByFnr(errorTx, utsending.fnr)?.let { person ->
                                                    AuditRepository.insert(errorTx, AuditTag.UTSENDING_FEILET, person.id!!, "Utsending feilet")
                                                }
                                                UtsendingRepository.increaseFailCount(errorTx, utsending.id, "SQL-feil, feil er logget til TEAM LOGS")
                                                feiledeUtsendingerOppdragzCounter.inc()
                                            }
                                        } catch (e: Exception) {
                                            logger.error("Feil under sending til oppdragz", e)
                                            dataSource.transaction { errorTx ->
                                                PersonRepository.findPersonByFnr(errorTx, utsending.fnr)?.let { person ->
                                                    AuditRepository.insert(errorTx, AuditTag.UTSENDING_FEILET, person.id!!, "Utsending feilet")
                                                }
                                                UtsendingRepository.increaseFailCount(errorTx, utsending.id, e.message ?: "Ukjent feil")
                                                feiledeUtsendingerOppdragzCounter.inc()
                                            }
                                        }
                                    }

                                    Forsystem.MANUELL -> {
                                        UtsendingRepository.delete(tx, utsending.id!!)
                                    }

                                    Forsystem.DARE_POC -> {
                                        if (utsendingDareClientService == null) {
                                            logger.error { "UtsendingDareClientService ikke tilgjengelig i prod" }
                                            return@transaction
                                        }

                                        try {
                                            logger.info { "Sender ut skattekort til Dare-Poc" }
                                            sendTilDarePoc(tx, utsending.fnr, utsending.inntektsaar)
                                            UtsendingRepository.delete(tx, utsending.id!!)
                                        } catch (e: Exception) {
                                            logger.error("Feil under sending til DARE POC", e)
                                            dataSource.transaction { errorTx ->
                                                PersonRepository.findPersonByFnr(errorTx, utsending.fnr)?.let { person ->
                                                    AuditRepository.insert(errorTx, AuditTag.UTSENDING_FEILET, person.id!!, "Utsending feilet")
                                                }
                                                UtsendingRepository.increaseFailCount(errorTx, utsending.id, e.message ?: "Ukjent feil")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        dataSource.transaction { tx ->
            UtsendingRepository.slettGamleBevis(tx)
        }
    }

    private fun sendTilDarePoc(
        tx: TransactionalSession,
        fnr: Personidentifikator,
        inntektsaar: Int,
    ) {
        val person = PersonRepository.findPersonByFnr(tx, fnr)
        val skattekort: Skattekort = SkattekortRepository.findAllByPersonId(tx, listOf(person?.id!!), listOf(inntektsaar), showOnlyLatest = true, adminRole = false).first()
        runBlocking {
            utsendingDareClientService?.sendSkattekort(
                skattekortDTO =
                    SkattekortDTO(
                        skattekort,
                        fnr,
                    ),
            )
            AuditRepository.insert(tx, AuditTag.UTSENDING_OK, person.id, "${Forsystem.DARE_POC.value}: Skattekort sendt til ${Forsystem.DARE_POC.value} OK")
        }
    }

    private fun sendTilOppdragz(
        tx: TransactionalSession,
        fnr: Personidentifikator,
        inntektsaar: Int,
        destination: String,
        jmsSession: Session,
        jmsProducer: MessageProducer,
    ) {
        var personId: PersonId? = null
        try {
            val person = PersonRepository.findPersonByFnr(tx, fnr)
            personId = person?.id ?: throw IllegalStateException("Fant ikke personidentifikator")
            val skattekort: Skattekort = SkattekortRepository.findAllByPersonId(tx, listOf(personId), listOf(inntektsaar), showOnlyLatest = true, adminRole = false).first()
            val copybook = SkattekortFixedRecordFormatter(skattekort, fnr.value).format()

            if (featureToggles.isBevisForSendingEnabled()) {
                UtsendingRepository.lagreBevis(tx, skattekort.id!!, Forsystem.OPPDRAGSSYSTEMET, fnr, copybook)
            }

            if (!copybook.trim().isEmpty()) {
                val message = jmsSession.createTextMessage(copybook)
                jmsProducer.send(message)
                AuditRepository.insert(tx, AuditTag.UTSENDING_OK, personId, "Oppdragz: Skattekort sendt til $destination")
            } else {
                AuditRepository.insert(tx, AuditTag.UTSENDING_OK, personId, "Oppdragz: Skattekort ikke sendt fordi skattekort-formatet ikke kan uttrykke innholdet")
            }
        } catch (e: Exception) {
            logger.error(e) { "Feil under sending til oppdragz, kø $destination" }
            personId?.let { id ->
                dataSource.transaction { errorsession ->
                    AuditRepository.insert(errorsession, AuditTag.UTSENDING_FEILET, id, "Oppdragz: Utsending feilet: $e")
                }
            }
            throw e
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
