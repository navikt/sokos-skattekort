package no.nav.sokos.lavendel.api

import io.kotest.core.spec.style.StringSpec
import jakarta.jms.ConnectionFactory
import jakarta.jms.Session
import org.apache.activemq.artemis.api.core.TransportConfiguration
import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl
import org.apache.activemq.artemis.core.remoting.impl.invm.InVMAcceptorFactory
import org.apache.activemq.artemis.core.server.embedded.EmbeddedActiveMQ
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory
import org.apache.activemq.artemis.jms.client.ActiveMQQueue

class TaImotOppdragTest : StringSpec({
    val queueName = "bestille.skattekort.queue"
    val embedded =
        EmbeddedActiveMQ()
            .setConfiguration(
                ConfigurationImpl()
                    .setPersistenceEnabled(false)
                    .setSecurityEnabled(false)
                    .addAcceptorConfiguration(TransportConfiguration(InVMAcceptorFactory::class.java.name)),
            )
    embedded.start()

    val connectionFactory: ConnectionFactory = ActiveMQConnectionFactory("vm://0")
    val queue = ActiveMQQueue(queueName)
    val skattekortbestillingsservice = Skattekortbestillingsservice(connectionFactory)

    "ta imot melding på kø" {
        connectionFactory.createConnection().use { connection ->
            connection.start()
            val session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)
            val oppdragssystemet = session.createProducer(queue)
            val skattekortbestillingsconsumer = session.createConsumer(queue)

            val testMessage = "OS;1994;15467834260"
            oppdragssystemet.send(session.createTextMessage(testMessage))

            val received = skattekortbestillingsconsumer.receive(1000)
            skattekortbestillingsservice.taImotOppdrag(received)
        }
    }

    afterSpec {
        embedded.stop()
    }
})
