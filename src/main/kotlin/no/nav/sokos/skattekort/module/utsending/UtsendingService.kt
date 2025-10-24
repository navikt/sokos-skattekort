package no.nav.sokos.skattekort.module.utsending

import com.zaxxer.hikari.HikariDataSource
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

import no.nav.sokos.skattekort.metrics.Metrics.prometheusMeterRegistry
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
    val dataSource: HikariDataSource,
    val jmsConnectionFactory: ConnectionFactory,
    @Named("leveransekoeOppdragZSkattekort") val leveransekoeOppdragZSkattekort: Queue,
) {
    private val logger = KotlinLogging.logger {}

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
            val skattekort = SkattekortRepository.findLatestByPersonId(tx, personId, inntektsaar)
            val skattekortmelding = Skattekortmelding(skattekort, fnr.toString())
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

    fun handleUtsending() {
        val utsendinger: List<Utsending> =
            dataSource.transaction { tx ->
                UtsendingRepository.getAllUtsendinger(tx)
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
                                // TODO: logg feil
                                dataSource.transaction { errorsession ->
                                    UtsendingRepository.increaseFailCount(errorsession, utsending.id, e.message ?: "Ukjent feil")
                                    feiledeUtsendingerOppdragzCounter.inc()
                                }
                            }
                        }
                        Forsystem.ARENA -> throw NotImplementedError("Forsystem.ARENA is not implemented yet")
                    }
                }
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
                .name("utsendinger_oppdragz_total")
                .help("Utsendinger til oppdrag z")
                .withoutExemplars()
                .register(prometheusMeterRegistry.prometheusRegistry)
        val feiledeUtsendingerOppdragzCounter =
            Counter
                .builder()
                .name("utsendinger_oppdragz_feil_total")
                .help("Feilede forsøk på utsendinger til oppdrag z")
                .withoutExemplars()
                .register(prometheusMeterRegistry.prometheusRegistry)
        val utsendingerIKoe =
            Gauge
                .builder()
                .name("utsendinger_i_koe")
                .help("Utsendinger i koe, enda ikke håndtert")
                .labelNames("status")
                .withoutExemplars()
                .register(prometheusMeterRegistry.prometheusRegistry)
    }
}
