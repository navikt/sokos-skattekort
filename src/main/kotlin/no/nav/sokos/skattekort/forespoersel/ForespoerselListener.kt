package no.nav.sokos.skattekort.forespoersel

import io.ktor.server.plugins.di.annotations.Named
import jakarta.jms.ConnectionFactory
import jakarta.jms.JMSConsumer
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
) : AutoCloseable {
    private var jmsContext: JMSContext? = null
    private var jmsConsumer: JMSConsumer? = null

    @Volatile
    private var isRunning = false

    @Synchronized
    private fun start() {
        jmsContext = connectionFactory.createContext(JMSContext.CLIENT_ACKNOWLEDGE)
        jmsConsumer = jmsContext!!.createConsumer(forespoerselQueue)

        jmsConsumer!!.setMessageListener { message: Message ->
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

        jmsContext!!.start()
        isRunning = true
        logger.info { "Forespoersel started, listening on queue: ${forespoerselQueue.queueName}" }
    }

    @Synchronized
    private fun stop() {
        if (!isRunning) return
        isRunning = false

        try {
            jmsContext?.stop() // Stop message delivery first
            jmsConsumer?.messageListener = null
            jmsConsumer?.close()
            jmsContext?.close() // This closes the underlying connection
            jmsConsumer = null
            jmsContext = null
        } catch (e: Exception) {
            logger.error(e) { "Error stopping ForespoerselListener" }
        }
        logger.info { "ForespoerselListener stopped on queue:  ${forespoerselQueue.queueName}" }
    }

    fun onOppdateringChanged(enabled: Boolean) {
        when {
            enabled && !isRunning -> start()
            !enabled && isRunning -> stop()
        }
    }

    override fun close() {
        stop()
    }
}
