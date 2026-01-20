package no.nav.sokos.skattekort.infrastructure

import io.kotest.core.listeners.AfterTestListener
import io.kotest.core.listeners.BeforeSpecListener
import io.kotest.core.test.TestCase
import io.kotest.engine.test.TestResult
import jakarta.jms.JMSContext
import jakarta.jms.Queue
import org.apache.activemq.artemis.api.core.SimpleString
import org.apache.activemq.artemis.api.core.TransportConfiguration
import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl
import org.apache.activemq.artemis.core.remoting.impl.invm.InVMAcceptorFactory
import org.apache.activemq.artemis.core.server.embedded.EmbeddedActiveMQ
import org.apache.activemq.artemis.core.settings.impl.AddressSettings
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory
import org.apache.activemq.artemis.jms.client.ActiveMQQueue

import no.nav.sokos.skattekort.JmsTestUtil
import no.nav.sokos.skattekort.config.PropertiesConfig

object MQListener : BeforeSpecListener, AfterTestListener {
    private val server: EmbeddedActiveMQ =
        EmbeddedActiveMQ()
            .setConfiguration(
                ConfigurationImpl()
                    .setPersistenceEnabled(false)
                    .setSecurityEnabled(false)
                    .addAcceptorConfiguration(TransportConfiguration(InVMAcceptorFactory::class.java.name))
                    .addAddressSetting(
                        "#",
                        AddressSettings().apply {
                            deadLetterAddress = SimpleString.of("DLQ")
                            expiryAddress = SimpleString.of("ExpiryQueue")
                        },
                    ),
            ).start()

    val connectionFactory: ActiveMQConnectionFactory by lazy {
        ActiveMQConnectionFactory("vm:localhost?create=false")
    }

    val forespoerselQueue: Queue = ActiveMQQueue(PropertiesConfig.getMQProperties().fraForSystemQueue)
    val forespoerselBoqQueue: Queue = ActiveMQQueue("${PropertiesConfig.getMQProperties().fraForSystemQueue}_BOQ")

    val utsendingsQueue: Queue = ActiveMQQueue(PropertiesConfig.getMQProperties().leveransekoeOppdragZSkattekort)
    val utsendingStorQueue: Queue = ActiveMQQueue(PropertiesConfig.getMQProperties().leveransekoeOppdragZSkattekortStor)
    val allQueues: List<Queue> = listOf(forespoerselQueue, utsendingsQueue, utsendingStorQueue)

    val jmsContext: JMSContext by lazy { connectionFactory.createContext() }

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
