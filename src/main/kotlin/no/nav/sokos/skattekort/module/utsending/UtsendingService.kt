package no.nav.sokos.skattekort.module.utsending

import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.plugins.di.annotations.Named
import jakarta.jms.Connection
import jakarta.jms.ConnectionFactory
import jakarta.jms.JMSContext
import jakarta.jms.MessageProducer
import jakarta.jms.Queue
import jakarta.jms.Session
import kotliquery.TransactionalSession

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
            personId?.let { id ->
                dataSource.transaction { errorsession ->
                    AuditRepository.insert(errorsession, AuditTag.UTSENDING_FEILET, id, "Oppdragz: Utsending feilet: ${e.message}")
                }
            }
        } finally {
            jmsProducer?.close()
            jmsSession?.close()
            jmsConnection?.close()
        }
    }
}
