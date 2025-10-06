package no.nav.sokos.skattekort

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import jakarta.jms.JMSContext
import jakarta.jms.Message

import no.nav.sokos.skattekort.config.PropertiesConfig
import no.nav.sokos.skattekort.listener.MQListener

class MqTest :
    FunSpec({
        extensions(listOf(MQListener))

        test("MQ Test") {
            println("Waiting for message...${PropertiesConfig.getMQProperties().fraForSystemQueue}")

            JmsTestUtil.sendMessage("Test message")

            val jmsContext = MQListener.getConnectionFactory().createContext(JMSContext.CLIENT_ACKNOWLEDGE)
            val bestillingsListener = jmsContext.createConsumer(MQListener.bestillingsQueue)

            bestillingsListener.setMessageListener { message: Message ->
                print(message)
                val msg = message.getBody(String::class.java)
                message.acknowledge()

                msg shouldBe "Test message"
            }
        }
    })
