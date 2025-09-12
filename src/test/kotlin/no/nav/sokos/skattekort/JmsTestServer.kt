package no.nav.sokos.skattekort

import jakarta.jms.Queue
import jakarta.jms.Session
import org.apache.activemq.artemis.api.core.TransportConfiguration
import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl
import org.apache.activemq.artemis.core.remoting.impl.invm.InVMAcceptorFactory
import org.apache.activemq.artemis.core.server.embedded.EmbeddedActiveMQ
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory

/**
 * IBM MQ er et stort beist som er vanskelig å enkelt containerifisere. Vi har
 * valgt å bruke activemq i test for å få testet at vi klarer å forholde oss til
 * å konsumere JMS-meldinger. En sideeffekt av dette er at vi
 * - ikke vil ha activemq-biblioteker i produksjons-classpath
 * - ikke vil bruke ibm-mq-spesifikke features fordi det vil øke området vi ikke har testdekning på
 * - når vi oppgraderer ibm mq-biblioteker må vi teste manuelt
 */
class JmsTestServer {
    val jmsTestServer: EmbeddedActiveMQ? =
        EmbeddedActiveMQ()
            .setConfiguration(
                ConfigurationImpl()
                    .setPersistenceEnabled(false)
                    .setSecurityEnabled(false)
                    .addAcceptorConfiguration(TransportConfiguration(InVMAcceptorFactory::class.java.name)),
            ).start()

    val jmsConnectionFactory = ActiveMQConnectionFactory("vm://0")

    fun sendMessage(
        queue: Queue,
        msg: String,
    ) {
        jmsConnectionFactory.createConnection().use { connection ->
            connection.start()
            val session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)
            val producer = session.createProducer(queue)
            producer.send(session.createTextMessage(msg))
            connection.close()
        }
    }

    fun assertQueueIsEmpty(queue: Queue) {
        jmsConnectionFactory.createConnection().use { connection ->
            connection.start()
            val session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)
            val browser = session.createBrowser(queue)
            if (browser.enumeration.hasMoreElements()) {
                throw AssertionError("Fant flere meldinger i active mq")
            }
            connection.close()
        }
    }

    init {
        jmsTestServer!!.start()
    }
}
