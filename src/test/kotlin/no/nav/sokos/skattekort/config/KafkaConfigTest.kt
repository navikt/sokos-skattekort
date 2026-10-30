package no.nav.sokos.skattekort.config

import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig
import io.confluent.kafka.serializers.subject.TopicNameStrategy
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class KafkaConfigTest :
    FunSpec({
        test("bruker topic-baserte subject-navn uten associations-oppslag") {
            val properties =
                KafkaConfig(
                    PropertiesConfig.KafkaProperties(
                        enabled = true,
                        topic = "test-topic",
                        consumerGroupId = "test-group",
                        offsetReset = "earliest",
                        brokers = "localhost:9092",
                        schemaRegistry = "mock://kafka-config-test",
                        schemaRegistryUser = "",
                        schemaRegistryPassword = "",
                        truststorePath = "",
                        credstorePassword = "",
                        keystorePath = "",
                    ),
                ).properties
            val config = KafkaAvroDeserializerConfig(properties)

            config.keySubjectNameStrategy()::class shouldBe TopicNameStrategy::class
            config.valueSubjectNameStrategy()::class shouldBe TopicNameStrategy::class
        }
    })
