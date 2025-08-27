package no.nav.sokos.skattekort

import io.kotest.core.spec.style.FunSpec
import jakarta.jms.Queue
import jakarta.jms.Session
import org.apache.activemq.artemis.core.server.embedded.EmbeddedActiveMQ
import org.apache.activemq.artemis.jms.client.ActiveMQQueue
import org.testcontainers.containers.PostgreSQLContainer

import no.nav.sokos.skattekort.config.DbListener
import no.nav.sokos.skattekort.config.MQListener

abstract class EndToEndFunSpec(
    body: FunSpec.(dbContainer: PostgreSQLContainer<Nothing>, jmsTestServer: EmbeddedActiveMQ) -> Unit,
) : FunSpec({
        extensions(listOf(MQListener, DbListener))

        val dbContainer = DbTestContainer().container
        val jmsTestServer: EmbeddedActiveMQ? = MQListener.server
        val allQueues = listOf(bestillingsQueue)

        fun assertAllQueuesAreEmpty() {
            MQListener.connectionFactory.createConnection().use { connection ->
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

        fun sendMessage(
            queue: Queue,
            msg: String,
        ) {
            MQListener.connectionFactory.createConnection().use { connection ->
                connection.start()
                val session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)
                val producer = session.createProducer(queue)
                producer.send(session.createTextMessage(msg))
                connection.close()
            }
        }

        fun assertQueueIsEmpty(queue: Queue) {
            MQListener.connectionFactory.createConnection().use { connection ->
                connection.start()
                val session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)
                val browser = session.createBrowser(queue)
                if (browser.enumeration.hasMoreElements()) {
                    throw AssertionError("Fant flere meldinger i active mq")
                }
                connection.close()
            }
        }

        afterTest {
            assertAllQueuesAreEmpty()
        }

        body(dbContainer, jmsTestServer!!)
    }) {
    val bestillingsQueue: Queue = ActiveMQQueue("bestillings-queue")
}
