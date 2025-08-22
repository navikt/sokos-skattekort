package no.nav.sokos.skattekort

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import jakarta.jms.JMSContext
import jakarta.jms.Message

import no.nav.sokos.skattekort.config.MQListener

class MqTest :
    FunSpec({
        extensions(listOf(MQListener))
        test("MQ Test") {
            MQListener.sendMessage("Test message")

            val jmsContext = MQListener.connectionFactory.createContext(JMSContext.CLIENT_ACKNOWLEDGE)
            val bestillingsListener = jmsContext.createConsumer(MQListener.bestillingMq)

            bestillingsListener.setMessageListener { message: Message ->
                print(message)
                val msg = message.getBody(String::class.java)
                message.acknowledge()

                msg shouldBe "Test message"
            }
        }
    })
