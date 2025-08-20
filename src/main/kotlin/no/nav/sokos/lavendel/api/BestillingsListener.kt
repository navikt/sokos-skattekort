package no.nav.sokos.lavendel.api

import jakarta.jms.ConnectionFactory
import jakarta.jms.JMSContext
import jakarta.jms.Message
import jakarta.jms.Queue

class BestillingsListener(
    jmsConnectionFactory: ConnectionFactory,
    bestillingsService: Skattekortbestillingsservice,
    bestillingsQueue: Queue,
) {
    private val jmsContext = jmsConnectionFactory.createContext(JMSContext.CLIENT_ACKNOWLEDGE)
    private val bestillingsListener = jmsContext.createConsumer(bestillingsQueue)

    // TODO: Legg til Opentelemetry trace
    // TODO: Feilhåndtering, send melding videre til dead letter queue, eller hva det heter lokalt
    init {
        bestillingsListener.setMessageListener { message: Message ->
            bestillingsService.taImotOppdrag(message)
            message.acknowledge()
        }
    }
}
