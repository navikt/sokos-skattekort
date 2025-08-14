package no.nav.sokos.lavendel.api
import jakarta.jms.ConnectionFactory
import jakarta.jms.Message
import jakarta.jms.Queue

import no.nav.sokos.lavendel.config.MQConfig

class Skattekortbestillingsservice(
    connectionFactory: ConnectionFactory = MQConfig.connectionFactory,
    bestilleSkattekortQueue: Queue,
//    private val dataSource: HikariDataSource = DatabaseConfig.dataSource,
) {
    fun taImotOppdrag(message: Message) {
        print("Hello, world! Received message: ${(message as? jakarta.jms.TextMessage)!!.text} from Skattekortbestillingsservice")
    }
}
