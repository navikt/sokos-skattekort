package no.nav.sokos.skattekort.infrastructure

import io.kotest.core.listeners.BeforeSpecListener
import io.kotest.core.spec.Spec
import mu.KotlinLogging
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.AdminClientConfig
import org.apache.kafka.clients.admin.NewTopic
import org.testcontainers.containers.wait.strategy.HostPortWaitStrategy
import org.testcontainers.kafka.KafkaContainer
import org.testcontainers.utility.DockerImageName

private val logger = KotlinLogging.logger {}

object KafkaListener : BeforeSpecListener {
    private val kafkaContainer =
        KafkaContainer(DockerImageName.parse("apache/kafka"))
            .waitingFor(HostPortWaitStrategy())

    private val adminClient: AdminClient by lazy {
        AdminClient.create(mapOf(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG to kafkaContainer.bootstrapServers))
    }

    override suspend fun beforeSpec(spec: Spec) {
        if (!kafkaContainer.isRunning) {
            kafkaContainer.start()
            setupShutdownHook()
            logger.info { "Kafka setup finished listening on ${kafkaContainer.bootstrapServers}." }
        }
    }

    fun createKafkaTopic(vararg topics: String) {
        val newTopics = topics.map { topic -> NewTopic(topic, 1, 1.toShort()) }
        adminClient.createTopics(newTopics)
    }

    private fun setupShutdownHook() {
        Runtime.getRuntime().addShutdownHook(
            Thread {
                cleanup()
                kafkaContainer?.stop()
            },
        )
    }

    private fun cleanup() {
        val topics = adminClient.listTopics().names().get()

        topics.forEach {
            adminClient.deleteTopics(listOf(it))
        }
    }
}
