package no.nav.sokos.skattekort

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import jakarta.jms.JMSContext
import jakarta.jms.Message

import no.nav.sokos.skattekort.infrastructure.MQListener

class MqTest :
    FunSpec({
        extensions(listOf(MQListener))

        test("MQ Test") {

            JmsTestUtil.sendMessage("Test message")

            val jmsContext = MQListener.connectionFactory.createContext(JMSContext.CLIENT_ACKNOWLEDGE)
            val bestillingsListener = jmsContext.createConsumer(MQListener.bestillingsQueue)

            bestillingsListener.setMessageListener { message: Message ->
                print(message)
                val msg = message.getBody(String::class.java)
                message.acknowledge()

                msg shouldBe "Test message"
            }
        }
    })
