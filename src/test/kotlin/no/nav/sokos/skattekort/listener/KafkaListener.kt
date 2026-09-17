package no.nav.sokos.skattekort.listener

import java.util.Properties
import java.util.concurrent.ExecutionException

import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig
import io.confluent.kafka.serializers.KafkaAvroSerializer
import io.kotest.core.listeners.BeforeSpecListener
import io.kotest.core.spec.Spec
import mu.KotlinLogging
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.AdminClientConfig
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.errors.TopicExistsException
import org.apache.kafka.common.security.auth.SecurityProtocol
import org.apache.kafka.common.serialization.StringSerializer
import org.testcontainers.containers.wait.strategy.HostPortWaitStrategy
import org.testcontainers.kafka.KafkaContainer
import org.testcontainers.utility.DockerImageName

import no.nav.person.pdl.leesah.Personhendelse
import no.nav.sokos.skattekort.config.KafkaConfig
import no.nav.sokos.skattekort.config.PropertiesConfig

private val logger = KotlinLogging.logger {}

object KafkaListener : BeforeSpecListener {
    const val MOCK_SCHEMA_REGISTRY_URL = "mock://sokos-skattekort-test"

    private val kafkaContainer =
        KafkaContainer(DockerImageName.parse("apache/kafka"))
            .waitingFor(HostPortWaitStrategy())

    private val adminClient: AdminClient by lazy {
        AdminClient.create(mapOf(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG to kafkaContainer.bootstrapServers))
    }

    val bootstrapServers: String
        get() = kafkaContainer.bootstrapServers

    override suspend fun beforeSpec(spec: Spec) {
        if (!kafkaContainer.isRunning) {
            kafkaContainer.start()
            setupShutdownHook()
            logger.info { "Kafka setup finished listening on ${kafkaContainer.bootstrapServers}." }
        }
    }

    fun getKafkaConfig(topic: String) =
        KafkaConfig(
            kafkaProperties =
                PropertiesConfig.KafkaProperties(
                    enabled = true,
                    topic = topic,
                    consumerGroupId = "sokos-skattekort-test-group",
                    offsetReset = "earliest",
                    brokers = kafkaContainer.bootstrapServers,
                    schemaRegistry = MOCK_SCHEMA_REGISTRY_URL,
                    schemaRegistryUser = "",
                    schemaRegistryPassword = "",
                    truststorePath = "",
                    credstorePassword = "",
                    keystorePath = "",
                ),
        ).also {
            it.properties[CommonClientConfigs.SECURITY_PROTOCOL_CONFIG] = SecurityProtocol.PLAINTEXT.name
        }

    fun createKafkaTopic(vararg topics: String) {
        adminClient
            .createTopics(topics.distinct().map { NewTopic(it, 1, 1) })
            .values()
            .forEach { (_, future) ->
                try {
                    future.get()
                } catch (exception: ExecutionException) {
                    if (exception.cause !is TopicExistsException) throw exception
                }
            }
    }

    val kafkaProducer by lazy {
        KafkaProducer<String, Personhendelse>(
            Properties().apply {
                put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KafkaListener.bootstrapServers)
                put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java.name)
                put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaAvroSerializer::class.java.name)
                put(AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, MOCK_SCHEMA_REGISTRY_URL)
            },
        )
    }

    private fun setupShutdownHook() {
        Runtime.getRuntime().addShutdownHook(
            Thread {
                cleanup()
                kafkaContainer.stop()
            },
        )
    }

    private fun cleanup() {
        val topics = adminClient.listTopics().names().get()
        topics.forEach { adminClient.deleteTopics(listOf(it)) }
    }
}
