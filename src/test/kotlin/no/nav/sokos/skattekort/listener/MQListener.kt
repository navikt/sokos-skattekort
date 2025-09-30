package no.nav.sokos.skattekort.listener

import io.kotest.core.listeners.AfterTestListener
import io.kotest.core.listeners.BeforeSpecListener
import io.kotest.core.spec.Spec
import io.kotest.core.test.TestCase
import io.kotest.engine.test.TestResult
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

import no.nav.sokos.skattekort.JmsTestUtil

const val MQ_FRA_FORSYSTEM_ALT_QUEUE = "QA.Q1_OS_ESKATT.FRA_FORSYSTEM_ALT"

object MQListener : BeforeSpecListener, AfterTestListener {
    val server: EmbeddedActiveMQ =
        EmbeddedActiveMQ()
            .setConfiguration(
                ConfigurationImpl()
                    .setPersistenceEnabled(false)
                    .setSecurityEnabled(false)
                    .addAcceptorConfiguration(TransportConfiguration(InVMAcceptorFactory::class.java.name)),
            )!!

    val connectionFactory: ConnectionFactory by lazy { ActiveMQConnectionFactory("vm:localhost?create=false") }
    val bestillingsQueue: Queue = ActiveMQQueue(MQ_FRA_FORSYSTEM_ALT_QUEUE)
    val allQueues: List<Queue> = listOf(bestillingsQueue)

    val jmsContext: JMSContext by lazy { connectionFactory.createContext() }
    val producer: JMSProducer by lazy { jmsContext.createProducer() }

    override suspend fun beforeSpec(spec: Spec) {
        println("jadda")
        server.start()
        println("jms status: " + server.activeMQServer.status)
    }

    override suspend fun afterAny(
        testCase: TestCase,
        result: TestResult,
    ) {
        super.afterTest(testCase, result)
        /* Meldinger som ligger igjen etter en test kan få en vilkårlig test mye senere til å feile litt tilfeldig.
        Det kan ta mye tid å finne ut av, derfor krever vi at tester ikke legger fra seg ting. */
        JmsTestUtil.assertAllQueuesAreEmpty()
    }
}
