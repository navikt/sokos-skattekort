package no.nav.sokos.skattekort

import jakarta.jms.JMSContext.SESSION_TRANSACTED
import jakarta.jms.Queue
import jakarta.jms.Session

import no.nav.sokos.skattekort.listener.MQListener
import no.nav.sokos.skattekort.listener.MQListener.allQueues
import no.nav.sokos.skattekort.listener.MQListener.bestillingsQueue
import no.nav.sokos.skattekort.listener.MQListener.producer

object JmsTestUtil {
    fun sendMessage(
        msg: String,
        queue: Queue = bestillingsQueue, // Vi bør fjerne defaulten her dersom vi ender opp med flere køer
    ) {
        MQListener.jmsContext.createContext(SESSION_TRANSACTED).use { context ->
            val message = context.createTextMessage(msg)
            producer.send(queue, message)
        }
    }

    fun assertQueueIsEmpty(queue: Queue) {
        MQListener.getConnectionFactory().createConnection().use { connection ->
            connection.start()
            val session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)
            val browser = session.createBrowser(queue)
            if (browser.enumeration.hasMoreElements()) {
                throw AssertionError("Fant flere meldinger i active mq")
            }
            connection.close()
        }
    }

    fun assertAllQueuesAreEmpty() {
        MQListener.getConnectionFactory().createConnection().use { connection ->
            connection.start()
            val session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)
            val results: List<String> =
                allQueues.mapNotNull { queue: Queue ->
                    val browser = session.createBrowser(queue)
                    if (browser.enumeration.hasMoreElements()) {
                        "Fant melding i kø " + queue.queueName
                    } else {
                        null
                    }
                }
            if (!results.isEmpty()) {
                throw AssertionError("Fant meldinger i active mq: " + results.joinToString(", "))
            }
            connection.close()
        }
    }
}
