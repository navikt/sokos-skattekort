package no.nav.sokos.lavendel

import jakarta.jms.Queue
import jakarta.jms.Session
import org.apache.activemq.artemis.api.core.TransportConfiguration
import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl
import org.apache.activemq.artemis.core.remoting.impl.invm.InVMAcceptorFactory
import org.apache.activemq.artemis.core.server.embedded.EmbeddedActiveMQ
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory
import org.apache.activemq.artemis.jms.client.ActiveMQQueue

/**
 * IBM MQ er et stort beist som er vanskelig å enkelt containerifisere. Vi har
 * valgt å bruke activemq i test for å få testet at vi klarer å forholde oss til
 * å konsumere JMS-meldinger. En sideeffekt av dette er at vi
 * - ikke vil ha activemq-biblioteker i produksjons-classpath
 * - ikke vil bruke ibm-mq-spesifikke features fordi det vil øke området vi ikke har testdekning på
 * - når vi oppgraderer ibm mq-biblioteker må vi teste manuelt
 */
class JmsTestServer {
    val jmsTestServer =
        EmbeddedActiveMQ()
            .setConfiguration(
                ConfigurationImpl()
                    .setPersistenceEnabled(false)
                    .setSecurityEnabled(false)
                    .addAcceptorConfiguration(TransportConfiguration(InVMAcceptorFactory::class.java.name)),
            )
    val bestillingsQueue: Queue = ActiveMQQueue("bestillings-queue")
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

    init {
        jmsTestServer.start()
    }
}
