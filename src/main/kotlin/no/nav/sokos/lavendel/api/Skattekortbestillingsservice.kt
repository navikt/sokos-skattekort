package no.nav.sokos.lavendel.api
import jakarta.jms.ConnectionFactory
import jakarta.jms.Message

import no.nav.sokos.lavendel.config.MQConfig

class Skattekortbestillingsservice(
    connectionFactory: ConnectionFactory = MQConfig.connectionFactory(),
) {
    fun taImotOppdrag(message: Message) {
        print("Hello, world! Received message: ${(message as? jakarta.jms.TextMessage)!!.text} from Skattekortbestillingsservice")
    }
}
