package no.nav.sokos.skattekort.forespoersel

import jakarta.jms.ConnectionFactory
import jakarta.jms.JMSContext
import jakarta.jms.Message
import jakarta.jms.Queue

class ForespoerselListener(
    jmsConnectionFactory: ConnectionFactory,
    forespoerselService: ForespoerselService,
    forespoerselQueue: Queue,
) {
    private val jmsContext = jmsConnectionFactory.createContext(JMSContext.CLIENT_ACKNOWLEDGE)
    private val listener = jmsContext.createConsumer(forespoerselQueue)

    // TODO: Legg til Opentelemetry trace
    // TODO: Feilhåndtering, send melding videre til dead letter queue, eller hva det heter lokalt
    init {
        listener.setMessageListener { message: Message ->
            println("Mottatt melding fra kø")
            forespoerselService.taImotForespoersel(message)
            message.acknowledge()
        }
    }
}
