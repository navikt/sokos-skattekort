package no.nav.sokos.lavendel.api
import com.ibm.mq.jakarta.jms.MQQueue
import jakarta.jms.ConnectionFactory
import jakarta.jms.Message
import jakarta.jms.Queue

import no.nav.sokos.lavendel.config.MQConfig
import no.nav.sokos.lavendel.config.PropertiesConfig

class Skattekortbestillingsservice(
    connectionFactory: ConnectionFactory = MQConfig.connectionFactory(),
    bestilleSkattekortQueue: Queue = MQQueue(PropertiesConfig.MQProperties().bestilleSkattekortQueueName),
//    private val dataSource: HikariDataSource = DatabaseConfig.dataSource,
) {
    fun taImotOppdrag(message: Message) {
        print("Hello, world! Received message: ${(message as? jakarta.jms.TextMessage)!!.text} from Skattekortbestillingsservice")
    }
}
