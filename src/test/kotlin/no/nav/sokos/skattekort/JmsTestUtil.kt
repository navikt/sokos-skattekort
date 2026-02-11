package no.nav.sokos.skattekort

import jakarta.jms.JMSContext.AUTO_ACKNOWLEDGE
import jakarta.jms.JMSContext.SESSION_TRANSACTED
import jakarta.jms.Message
import jakarta.jms.Queue

import no.nav.sokos.skattekort.listener.MQListener
import no.nav.sokos.skattekort.listener.MQListener.allQueues

object JmsTestUtil {
    fun sendMessage(
        msg: String,
        queue: Queue,
    ) {
        MQListener.jmsContext.createContext(SESSION_TRANSACTED).use { context ->
            val message = context.createTextMessage(msg)
            val producer = context.createProducer()
            producer.send(queue, message)
            context.commit()
        }
    }

    fun getMessages(queue: Queue): List<String> =
        MQListener.jmsContext.createContext(AUTO_ACKNOWLEDGE).use { context ->
            val consumer = context.createConsumer(queue)
            val messages = mutableListOf<String>()
            var msg: Message? = consumer.receive(100)
            while (msg != null) {
                messages.add(msg.getBody(String::class.java))
                msg = consumer.receive(100)
            }
            consumer.close()
            messages
        }

    fun assertQueueIsEmpty(queue: Queue) {
        MQListener.jmsContext.createContext(AUTO_ACKNOWLEDGE).use { context ->
            val browser = context.createBrowser(queue)
            if (browser.enumeration.hasMoreElements()) {
                throw AssertionError("Fant flere meldinger i active mq")
            }
            browser.close()
        }
    }

    fun assertAllQueuesAreEmpty() {
        MQListener.jmsContext.createContext(AUTO_ACKNOWLEDGE).use { context ->
            val results: List<String> =
                allQueues.mapNotNull { queue: Queue ->
                    val browser = context.createBrowser(queue)
                    if (browser.enumeration.hasMoreElements()) {
                        "Fant melding i kø " + queue.queueName
                    } else {
                        null
                    }
                }
            if (!results.isEmpty()) {
                throw AssertionError("Fant meldinger i active mq: " + results.joinToString(", "))
            }
        }
    }
}
