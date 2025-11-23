package no.nav.sokos.skattekort.module.utsending

import javax.sql.DataSource

import io.ktor.server.plugins.di.annotations.Named
import io.prometheus.metrics.core.metrics.Counter
import io.prometheus.metrics.core.metrics.Gauge
import jakarta.jms.Connection
import jakarta.jms.ConnectionFactory
import jakarta.jms.JMSContext
import jakarta.jms.MessageProducer
import jakarta.jms.Queue
import jakarta.jms.Session
import kotliquery.TransactionalSession
import mu.KotlinLogging

import no.nav.sokos.skattekort.infrastructure.METRICS_NAMESPACE
import no.nav.sokos.skattekort.infrastructure.Metrics.prometheusMeterRegistry
import no.nav.sokos.skattekort.infrastructure.UnleashIntegration
import no.nav.sokos.skattekort.module.forespoersel.Forsystem
import no.nav.sokos.skattekort.module.person.AuditRepository
import no.nav.sokos.skattekort.module.person.AuditTag
import no.nav.sokos.skattekort.module.person.PersonId
import no.nav.sokos.skattekort.module.person.PersonRepository
import no.nav.sokos.skattekort.module.person.Personidentifikator
import no.nav.sokos.skattekort.module.skattekort.SkattekortRepository
import no.nav.sokos.skattekort.module.utsending.oppdragz.SkattekortFixedRecordFormatter
import no.nav.sokos.skattekort.module.utsending.oppdragz.Skattekortmelding
import no.nav.sokos.skattekort.util.SQLUtils.transaction

class UtsendingService(
    private val dataSource: DataSource,
    private val jmsConnectionFactory: ConnectionFactory,
    @Named("leveransekoeOppdragZSkattekort") private val leveransekoeOppdragZSkattekort: Queue,
    private val featureToggles: UnleashIntegration,
) {
    private val logger = KotlinLogging.logger {}

    fun handleUtsending() {
        if (featureToggles.isUtsendingEnabled()) {
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
            utsendinger
                .forEach { utsending ->
                    dataSource.transaction { tx ->
                        when (utsending.forsystem) {
                            Forsystem.OPPDRAGSSYSTEMET -> {
                                try {
                                    sendTilOppdragz(tx, utsending.fnr, utsending.inntektsaar)
                                    UtsendingRepository.delete(tx, utsending.id!!)
                                    utsendingOppdragzCounter.inc()
                                } catch (e: Exception) {
                                    logger.error("Feil under sending til oppdragz", e)
                                    dataSource.transaction { errorTx ->
                                        PersonRepository.findPersonByFnr(errorTx, utsending.fnr)?.let { person ->
                                            AuditRepository.insert(errorTx, AuditTag.UTSENDING_FEILET, person.id!!, "Utsending feilet: ${e.message}")
                                        }
                                        UtsendingRepository.increaseFailCount(errorTx, utsending.id, e.message ?: "Ukjent feil")
                                        feiledeUtsendingerOppdragzCounter.inc()
                                    }
                                }
                            }
                            Forsystem.MANUELL -> {
                                UtsendingRepository.delete(tx, utsending.id!!)
                            }
                        }
                    }
                }
        } else {
            logger.debug("Utsending er disablet")
        }
    }

    private fun sendTilOppdragz(
        tx: TransactionalSession,
        fnr: Personidentifikator,
        inntektsaar: Int,
    ) {
        var jmsConnection: Connection? = null
        var jmsSession: Session? = null
        var jmsProducer: MessageProducer? = null
        var personId: PersonId? = null
        try {
            val person = PersonRepository.findPersonByFnr(tx, fnr)
            personId = person?.id ?: throw IllegalStateException("Fant ikke personidentifikator")
            jmsConnection = jmsConnectionFactory.createConnection()
            jmsSession = jmsConnection.createSession(JMSContext.AUTO_ACKNOWLEDGE)
            jmsProducer = jmsSession.createProducer(leveransekoeOppdragZSkattekort)
            val skattekort = SkattekortRepository.findLatestByPersonId(tx, personId, inntektsaar, adminRole = false)
            val skattekortmelding = Skattekortmelding(skattekort, fnr.value)
            val copybook = SkattekortFixedRecordFormatter(skattekortmelding, inntektsaar.toString()).format()
            val message = jmsSession.createTextMessage(copybook)
            jmsProducer.send(message)
            AuditRepository.insert(tx, AuditTag.UTSENDING_OK, personId, "Oppdragz: Skattekort sendt")
        } catch (e: Exception) {
            logger.error(e) { "Feil under sending til oppdragz" }
            personId?.let { id ->
                dataSource.transaction { errorsession ->
                    AuditRepository.insert(errorsession, AuditTag.UTSENDING_FEILET, id, "Oppdragz: Utsending feilet: $e")
                }
            }
            throw e
        } finally {
            jmsProducer?.close()
            jmsSession?.close()
            jmsConnection?.close()
        }
    }

    fun getAllUtsendinger(): List<Utsending> =
        dataSource.transaction { tx ->
            UtsendingRepository.getAllUtsendinger(tx)
        }

    companion object {
        val utsendingOppdragzCounter =
            Counter
                .builder()
                .name("${METRICS_NAMESPACE}_utsendinger_oppdragz_total")
                .help("Utsendinger til oppdrag z")
                .withoutExemplars()
                .register(prometheusMeterRegistry.prometheusRegistry)
        val feiledeUtsendingerOppdragzCounter =
            Counter
                .builder()
                .name("${METRICS_NAMESPACE}_utsendinger_oppdragz_feil_total")
                .help("Feilede forsøk på utsendinger til oppdrag z")
                .withoutExemplars()
                .register(prometheusMeterRegistry.prometheusRegistry)
        val utsendingerIKoe =
            Gauge
                .builder()
                .name("${METRICS_NAMESPACE}_utsendinger_i_koe")
                .help("Utsendinger i koe, enda ikke håndtert")
                .labelNames("status")
                .withoutExemplars()
                .register(prometheusMeterRegistry.prometheusRegistry)
    }
}
