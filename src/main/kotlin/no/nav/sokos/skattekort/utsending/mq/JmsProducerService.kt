package no.nav.sokos.skattekort.utsending.mq

import com.ibm.msg.client.jakarta.jms.JmsConstants.SESSION_TRANSACTED
import io.prometheus.metrics.core.metrics.Counter
import jakarta.jms.ConnectionFactory
import jakarta.jms.JMSContext
import jakarta.jms.JMSProducer
import jakarta.jms.Queue
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

open class JmsProducerService(
    connectionFactory: ConnectionFactory,
    private val senderQueue: Queue,
    private val metricCounter: Counter,
) {
    private val jmsContext: JMSContext = connectionFactory.createContext()
    private val producer: JMSProducer = jmsContext.createProducer()

    open fun send(payload: List<String>) {
        jmsContext.createContext(SESSION_TRANSACTED).use { context ->
            val messages =
                payload.map {
                    context.createTextMessage(
                        truncate(it),
                    )
                }
            runCatching {
                messages.forEach { message ->
                    producer.send(senderQueue, message)
                }
            }.onSuccess {
                context.commit()
                metricCounter.inc(payload.size.toLong())
                logger.debug { "MQ-transaksjon committed ${messages.size} meldinger" }
            }.onFailure { exception ->
                context.rollback()
                logger.error(exception) { "MQ-transaksjon rolled back" }
            }
        }
    }

    private fun truncate(msg: String): String = msg.replace(Regex(">\\s+<"), "><")
}
