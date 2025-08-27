package no.nav.sokos.skattekort.config

import com.ibm.msg.client.jakarta.jms.JmsConstants
import io.kotest.core.listeners.TestListener
import io.kotest.core.spec.Spec
import jakarta.jms.ConnectionFactory
import jakarta.jms.JMSContext
import jakarta.jms.JMSProducer
import jakarta.jms.Queue
import org.apache.activemq.artemis.api.core.TransportConfiguration
import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl
import org.apache.activemq.artemis.core.remoting.impl.invm.InVMAcceptorFactory
import org.apache.activemq.artemis.core.server.embedded.EmbeddedActiveMQ
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory
import org.apache.activemq.artemis.jms.client.ActiveMQQueue

object MQListener : TestListener {
    val server =
        EmbeddedActiveMQ()
            .setConfiguration(
                ConfigurationImpl()
                    .setPersistenceEnabled(false)
                    .setSecurityEnabled(false)
                    .addAcceptorConfiguration(TransportConfiguration(InVMAcceptorFactory::class.java.name)),
            ).start()

    var connectionFactory: ConnectionFactory = ActiveMQConnectionFactory("vm:localhost?create=false")
    val bestillingMq = ActiveMQQueue("bestillings-queue")

    private val jmsContext: JMSContext = connectionFactory.createContext()
    private val producer: JMSProducer = jmsContext.createProducer()

    fun sendMessage(
        msg: String,
        queue: Queue = bestillingMq,
    ) {
        jmsContext.createContext(JmsConstants.SESSION_TRANSACTED).use { context ->
            val message = context.createTextMessage(msg)
            producer.send(queue, message)
        }
    }

    override suspend fun afterSpec(spec: Spec) {
        server.stop()
    }
}
