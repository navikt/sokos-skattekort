package no.nav.sokos.lavendel.api

import io.kotest.core.spec.style.StringSpec
import io.ktor.server.config.ApplicationConfig
import jakarta.jms.ConnectionFactory
import jakarta.jms.Session
import org.apache.activemq.artemis.api.core.TransportConfiguration
import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl
import org.apache.activemq.artemis.core.remoting.impl.invm.InVMAcceptorFactory
import org.apache.activemq.artemis.core.server.embedded.EmbeddedActiveMQ
import org.apache.activemq.artemis.jms.client.ActiveMQQueue

import no.nav.sokos.lavendel.TestUtil
import no.nav.sokos.lavendel.config.CompositeApplicationConfig
import no.nav.sokos.lavendel.config.MQConfig

class TaImotOppdragTest :
    StringSpec({
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

        val properties = CompositeApplicationConfig(TestUtil.getOverrides(embedded), ApplicationConfig("application.conf"))

        MQConfig.init(properties)

        val connectionFactory: ConnectionFactory = MQConfig.connectionFactory
        val queue = ActiveMQQueue(queueName)
        val skattekortbestillingsservice = Skattekortbestillingsservice(connectionFactory, queue)

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

                // brukeren skal være opprettet
                // et "oppdrag" på å bestille skattekort skal lagres, klart til neste batch-sending
                // oppdragz skal registreres som en interessent
            }
        }

        afterSpec {
            embedded.stop()
        }
    })
