package no.nav.sokos.skattekort.module.forespoersel

import io.ktor.server.plugins.di.annotations.Named
import jakarta.jms.ConnectionFactory
import jakarta.jms.JMSContext
import jakarta.jms.Message
import jakarta.jms.Queue
import mu.KotlinLogging

private val logger = KotlinLogging.logger { }

class ForespoerselListener(
    private val connectionFactory: ConnectionFactory,
    private val forespoerselService: ForespoerselService,
    @Named("forespoerselQueue") private val forespoerselQueue: Queue,
) {
    private lateinit var jmsContext: JMSContext

    fun start() {
        // TODO: Legg til Opentelemetry trace
        // TODO: Feilhåndtering, send melding videre til dead letter queue, eller hva det heter lokalt

        jmsContext = connectionFactory.createContext(JMSContext.CLIENT_ACKNOWLEDGE)
        val listener = jmsContext.createConsumer(forespoerselQueue)

        listener.setMessageListener { message: Message ->
            val jmsMessage = message.getBody(String::class.java)
            forespoerselService.taImotForespoersel(jmsMessage)
            message.acknowledge()
        }

        jmsContext.start()
        logger.info { "Forespoersel started, listening on queue: ${forespoerselQueue.queueName}" }
    }
}
