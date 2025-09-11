package no.nav.sokos.skattekort

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import jakarta.jms.JMSContext
import jakarta.jms.Message

import no.nav.sokos.skattekort.config.JmsListener

class MqTest :
    FunSpec({
        extensions(listOf(JmsListener))
        test("MQ Test") {
            JmsTestUtil.sendMessage("Test message")

            val jmsContext = JmsListener.connectionFactory.createContext(JMSContext.CLIENT_ACKNOWLEDGE)
            val bestillingsListener = jmsContext.createConsumer(JmsListener.bestillingsQueue)

            bestillingsListener.setMessageListener { message: Message ->
                print(message)
                val msg = message.getBody(String::class.java)
                message.acknowledge()

                msg shouldBe "Test message"
            }
        }
    })
