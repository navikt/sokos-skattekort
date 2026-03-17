package no.nav.sokos.skattekort.forespoersel

import io.ktor.server.plugins.di.annotations.Named
import jakarta.jms.ConnectionFactory
import jakarta.jms.JMSContext
import jakarta.jms.Message
import jakarta.jms.Queue
import mu.KotlinLogging

import no.nav.sokos.skattekort.util.TraceUtils

private val logger = KotlinLogging.logger { }

class ForespoerselListener(
    private val connectionFactory: ConnectionFactory,
    private val forespoerselService: ForespoerselService,
    @Named("forespoerselQueue") private val forespoerselQueue: Queue,
    @Named("forespoerselBoqQueue") private val forespoerselBoqQueue: Queue,
) {
    private val jmsContext = connectionFactory.createContext(JMSContext.CLIENT_ACKNOWLEDGE)
    private val messageListener = jmsContext.createConsumer(forespoerselQueue)

    init {
        messageListener.setMessageListener { message: Message ->
            TraceUtils.withTracerId {
                runCatching {
                    val jmsMessage = message.getBody(String::class.java)
                    forespoerselService.taImotForespoersel(jmsMessage)
                    message.acknowledge()
                }.onFailure {
                    val boqProducer = jmsContext!!.createProducer()
                    boqProducer.send(forespoerselBoqQueue, message)
                    message.acknowledge()
                    logger.error { "Send to BOQ with messageId: ${message.jmsMessageID}" }
                }
            }
        }
    }

    fun start() {
        jmsContext.start()
    }
}
